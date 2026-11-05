package com.example.gemini_backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PlannerController {

    record Creds(String username, String password) {}

    private final UserRepo users;
    private final TaskRepo tasks;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PlannerController(UserRepo users, TaskRepo tasks) {
        this.users = users;
        this.tasks = tasks;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Creds c) {
        if (c.username() == null || c.username().isBlank()
                || c.password() == null || c.password().length() < 6) {
            return error(400, "Enter a username and a password of at least 6 characters.");
        }
        String name = c.username().trim().toLowerCase();
        if (users.findByUsername(name).isPresent()) {
            return error(409, "That username is taken. Try another one.");
        }
        User u = new User();
        u.username = name;
        u.passwordHash = encoder.encode(c.password());
        return session(u);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Creds c) {
        String name = c.username() == null ? "" : c.username().trim().toLowerCase();
        User u = users.findByUsername(name).orElse(null);
        if (u == null || c.password() == null || !encoder.matches(c.password(), u.passwordHash)) {
            return error(401, "Wrong username or password.");
        }
        return session(u);
    }

    private ResponseEntity<?> session(User u) {
        u.token = UUID.randomUUID().toString();
        users.save(u);
        return ResponseEntity.ok(Map.of("token", u.token, "username", u.username));
    }

    private ResponseEntity<?> error(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    private User auth(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByToken(header.substring(7))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/tasks")
    public List<Task> list(@RequestHeader(value = "Authorization", required = false) String h) {
        return tasks.findByUserId(auth(h).id);
    }

    @PostMapping("/tasks")
    public Task create(@RequestHeader(value = "Authorization", required = false) String h,
                       @RequestBody Task t) {
        t.id = null;
        t.userId = auth(h).id;
        return tasks.save(t);
    }

    @PutMapping("/tasks/{id}")
    public Task update(@RequestHeader(value = "Authorization", required = false) String h,
                       @PathVariable Long id, @RequestBody Task in) {
        Task t = owned(h, id);
        t.title = in.title;
        t.day = in.day;
        t.time = in.time;
        t.done = in.done;
        return tasks.save(t);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "Authorization", required = false) String h,
                                       @PathVariable Long id) {
        tasks.delete(owned(h, id));
        return ResponseEntity.noContent().build();
    }

    private Task owned(String h, Long id) {
        Long uid = auth(h).id;
        return tasks.findById(id)
                .filter(t -> t.userId.equals(uid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}