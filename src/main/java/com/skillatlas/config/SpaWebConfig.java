package com.skillatlas.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// Serves the Angular build that the Dockerfile copies into classpath:/static/.
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource requested = location.createRelative(path);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Angular routes are client-side, so a hard refresh on /people/123 lands here
                        // and must get index.html. The API must not: it would answer a missing
                        // endpoint with an HTML page and status 200. Paths arrive without a leading /.
                        if (path.startsWith("api/") || path.startsWith("actuator/")) {
                            return null;
                        }
                        // Absent in a plain `mvnw spring-boot:run`, where static/ is empty — the 404
                        // that dev already returns is the right answer there.
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
