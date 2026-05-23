package com.example.flexhub.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.service.OpenAPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

// Verifies that @Configuration classes are themselves Spring beans (not just bean factories).
// Uses Testcontainers Postgres via AbstractIntegrationTest because @SpringBootTest needs a
// working DataSource — disabling it cleanly is more trouble than spinning up Postgres.
class ConfigurationClassesAreBeansTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SpringDocConfiguration springDocConfiguration;

    @Autowired
    private SwaggerUiConfigProperties swaggerUiConfigProperties;

    @Autowired
    private OpenAPIService openAPIService;

    @Test
    void springDocConfigurationIsRegisteredAsABean() {
        assertThat(springDocConfiguration).isNotNull();
        assertThat(context.getBeanNamesForType(SpringDocConfiguration.class)).isNotEmpty();
    }

    @Test
    void swaggerUiConfigPropertiesIsRegisteredAsABean() {
        assertThat(swaggerUiConfigProperties).isNotNull();
        assertThat(context.getBeanNamesForType(SwaggerUiConfigProperties.class)).isNotEmpty();
    }

    @Test
    void openAPIServiceIsRegisteredAsABean() {
        assertThat(openAPIService).isNotNull();
        assertThat(context.getBeanNamesForType(OpenAPIService.class)).isNotEmpty();
    }

    @Test
    void allSpringdocBeansAppearInTheContext() {
        String[] beanNames = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.toLowerCase().contains("springdoc")
                        || name.toLowerCase().contains("swagger")
                        || name.toLowerCase().contains("openapi"))
                .sorted()
                .toArray(String[]::new);

        System.out.println("--- Springdoc-related beans in the context ---");
        Arrays.stream(beanNames).forEach(System.out::println);
        System.out.println("--- " + beanNames.length + " beans total ---");
        assertThat(beanNames).isNotEmpty();
    }
}
