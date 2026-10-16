package it.quackfit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Attiva i metodi @Scheduled (es. la pulizia dei contatori del limite per IP). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
