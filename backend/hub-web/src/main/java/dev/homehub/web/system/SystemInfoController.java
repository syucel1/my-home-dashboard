package dev.homehub.web.system;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** First API endpoint: proves the web module is wired into the app and reports what is running. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final SystemInfo info;

    public SystemInfoController(
            @Value("${spring.application.name:home-hub}") String name, ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = (build != null) ? build.getVersion() : "dev";
        this.info = new SystemInfo(name, version, Runtime.version().toString());
    }

    @GetMapping("/info")
    public SystemInfo info() {
        return info;
    }
}
