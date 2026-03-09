package com.fix.fepgateway.support;

import com.fix.fepgateway.exception.GlobalExceptionHandler;
import jakarta.servlet.Filter;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public final class FepGatewayStandaloneMvcSupport {

  private FepGatewayStandaloneMvcSupport() {
  }

  public static MockMvc build(List<Filter> filters, Object... controllers) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    var builder = MockMvcBuilders.standaloneSetup(controllers)
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator);

    filters.forEach(builder::addFilters);
    return builder.build();
  }
}
