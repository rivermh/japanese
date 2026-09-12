package com.japanese.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "levels")
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "level_system", nullable = false, length = 40)
    private String system;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    protected Level() {
    }

    public Level(String system, String code, String name) {
        this.system = system;
        this.code = code;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getSystem() {
        return system;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
