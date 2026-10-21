package com.lexbridge.decision;

import java.nio.file.*;
import org.apache.catalina.servlets.DefaultServlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
class CaseFileStaticConfig {
  @Bean
  ServletRegistrationBean<DefaultServlet> caseFilesServlet() {
    ServletRegistrationBean<DefaultServlet> reg =
        new ServletRegistrationBean<>(new DefaultServlet(), "/case-files/*");
    reg.addInitParameter("listings", "true");
    reg.addInitParameter("readonly", "true");
    reg.setName("caseFiles");
    return reg;
  }

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> caseFilesBase(
      @Value("${app.upload-dir}") String dir) {
    return factory ->
        factory.addContextCustomizers(
            ctx -> ctx.setDocBase(Paths.get(dir).toAbsolutePath().normalize().toString()));
  }
}
