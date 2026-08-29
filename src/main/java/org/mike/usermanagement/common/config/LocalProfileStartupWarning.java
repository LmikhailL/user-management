package org.mike.usermanagement.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalProfileStartupWarning implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalProfileStartupWarning.class);

    @Override
    public void run(ApplicationArguments args) {
        log.warn("Running with the 'local' profile: expecting PostgreSQL reachable at "
                + "localhost:5432 (database 'user_management'). Make sure the Postgres "
                + "Docker container is started before using the app.");
    }
}
