package com.fix.channel.convention;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ChannelModelAttributeConventionTest {

  @Test
  void requestDtoParametersInControllersMustDeclareExplicitBinding() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    Set<Class<?>> controllerClasses = scanner.findCandidateComponents("com.fix.channel.controller").stream()
        .map(beanDefinition -> {
          try {
            return Class.forName(beanDefinition.getBeanClassName());
          } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
          }
        })
        .collect(java.util.stream.Collectors.toSet());

    for (Class<?> controllerClass : controllerClasses) {
      for (Method method : controllerClass.getDeclaredMethods()) {
        for (Parameter parameter : method.getParameters()) {
          String parameterTypeName = parameter.getType().getName();
          if (parameterTypeName.contains(".dto.request.")) {
            boolean explicitBinding = parameter.isAnnotationPresent(ModelAttribute.class)
                || parameter.isAnnotationPresent(RequestBody.class);
            assertThat(explicitBinding)
                .as("%s#%s parameter %s must declare @ModelAttribute or @RequestBody",
                    controllerClass.getSimpleName(),
                    method.getName(),
                    parameter.getName())
                .isTrue();
          }
        }
      }
    }
  }
}
