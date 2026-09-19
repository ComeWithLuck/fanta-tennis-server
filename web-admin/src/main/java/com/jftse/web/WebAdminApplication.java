package com.jftse.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Player account site + GM panel. Local addition (not upstream JFTSE): it shares the emulator's
 * database and entity classes, so anything it writes is exactly what the game servers read.
 */
@SpringBootApplication
@EnableAsync                     // Telegram notifications must never hold up a top-up
@EntityScan(basePackages = "com.jftse.entities")
@EnableJpaRepositories(basePackages = "com.jftse.entities")
// com.jftse.server.core.service brings InventoryService & friends, so giving an item goes through the
// exact same code the in-game shop uses (stacking, pocket counters, house/pet side effects)
// ...plus com.jftse.emulator.server.core.service.impl, where every server module keeps its own copy of
// the few service implementations that are not in server-core (ClothEquipmentServiceImpl) - we do the same
@ComponentScan(basePackages = { "com.jftse.web", "com.jftse.server.core.service",
        "com.jftse.server.core.jdbc", "com.jftse.emulator.common.service",
        "com.jftse.emulator.server.core.service.impl" })
public class WebAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebAdminApplication.class, args);
    }

    /** Same encoder the auth-server uses, for when password.encryption.enabled is on. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
