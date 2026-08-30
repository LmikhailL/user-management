package org.mike.usermanagement;

import org.springframework.boot.SpringApplication;

public class TestUserManagementApplication {

    static void main(String[] args) {
        SpringApplication.from(UserManagementApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
