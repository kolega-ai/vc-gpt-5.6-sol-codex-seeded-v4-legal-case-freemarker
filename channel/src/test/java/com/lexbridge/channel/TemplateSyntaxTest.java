package com.lexbridge.channel;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import freemarker.template.Configuration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSyntaxTest {
  @Test
  void everyScreenTemplateParsesWithHtmlEscapingEnabled() throws Exception {
    Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
    configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
    configuration.setOutputFormat(freemarker.core.HTMLOutputFormat.INSTANCE);

    for (String name :
        List.of(
            "layout.ftlh",
            "login.ftlh",
            "forgot.ftlh",
            "reset.ftlh",
            "dashboard.ftlh",
            "matters.ftlh",
            "matter-form.ftlh",
            "matter-edit.ftlh",
            "matter-detail.ftlh",
            "document.ftlh",
            "clients.ftlh",
            "calendar.ftlh",
            "search.ftlh",
            "reports.ftlh",
            "users.ftlh",
            "settings.ftlh",
            "audit.ftlh",
            "imports.ftlh",
            "integrations.ftlh",
            "profile.ftlh",
            "error.ftlh")) {
      assertNotNull(configuration.getTemplate(name), name);
    }
  }
}
