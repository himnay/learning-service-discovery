package com.learning.discovery.user.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

// "user" is a reserved SQL word, hence the plural table name.
@Table("users")
public record User(@Id Long id, String name, String email) {
}
