package com.learning.discovery.user.repository;

import com.learning.discovery.user.model.User;
import org.springframework.data.repository.ListCrudRepository;

public interface UserRepository extends ListCrudRepository<User, Long> {
}
