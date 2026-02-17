package com.bookstore.enterprise.domain.model;

public class Author {

    private Long id;
    private String name;
    private String bio;

    public Author() {
    }

    public Author(Long id, String name, String bio) {
        this.id = id;
        this.name = name;
        this.bio = bio;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
