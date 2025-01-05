package com.example.tomyongji.auth.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "email_verification")
public class EmailVerification {
    @Id
    private long id;
    private String email;
    private String verificationCode;
    private LocalDateTime verificatedAt;
    @OneToOne
    @JsonBackReference
    private User user;
}
