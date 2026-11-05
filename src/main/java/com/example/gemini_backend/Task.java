package com.example.gemini_backend;

import jakarta.persistence.*;

@Entity
@Table(name = "tasks")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "user_id", nullable = false)
    public Long userId;
    public String title;
        @Column(name = "task_day")
    public String day;
    @Column(name = "start_time")
    public String time;
    public boolean done;
}