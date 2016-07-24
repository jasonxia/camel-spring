package org.paradise;

import io.hawt.springboot.EnableHawtio;
import io.hawt.web.AuthenticationFilter;
import org.apache.camel.Processor;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.spring.boot.FatJarRouter;
import org.paradise.service.RedisServiceBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import redis.clients.jedis.exceptions.JedisConnectionException;

@SpringBootApplication
@EnableHawtio
@EnableDiscoveryClient
@EnableFeignClients
@EnableCircuitBreaker
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
public class MainSpringBootCamelRouter extends FatJarRouter {

	private static final Logger logger = LoggerFactory.getLogger(MainSpringBootCamelRouter.class);

    private static final Processor enrichExchangeBody = exchange -> exchange.getIn().setBody("[" + exchange.getIn().getBody().toString() + "]");

    @Autowired
    RedisServiceBean redisServiceBean;

	@Bean
	public AlwaysSampler defaultSampler() {

		return new AlwaysSampler();
	}

    @Bean
    String myBean() {

        return "What a beautiful Spring Bean!";
    }

    public static void main(String[] args) {

		System.setProperty(AuthenticationFilter.HAWTIO_AUTHENTICATION_ENABLED, "false");

		logger.debug("Start Spring Boot Camel Router Service ...");

		SpringApplication.run(MainSpringBootCamelRouter.class, args);

		logger.debug("Happy Spring Boot Camel Router Service ...");
	}

	@Override
	public void configure() {

        // Hello Camel example
		from("timer:trigger?fixedRate=true&period=20000")
                .routeId("Hello Camel")
				// hard code break point
				.process(exchange -> System.out.println())
				// Add the following line before a bean, for example:
				//
				//   .to("file:///Users/terrence/Projects/pcc")
				//   .to("cxf:bean:labelPrintServiceEndpoint?dataFormat=POJO")
				//
				// under "/Users/terrence/Projects/pcc" directory, HTTP/SOAP request will put into file e.g.
                // "ID-muffler-53122-1459400952912-0-11"
				.transform().simple("ref:myBean")
				.to("log:out");

        // Camel Redis publish
        from("timer://redis?period=20s")
                .routeId("Redis publish")
                .onException(JedisConnectionException.class)
                    .handled(true)
                    .transform().simple("${exception.message}")
                    .bean(redisServiceBean, "handleException(${body})")
                    .to("mock:error")
                .end()
                // publish and subscribe
                .setHeader(RedisConstants.CHANNEL, constant("camelChannel"))
                .setHeader(RedisConstants.COMMAND, constant("PUBLISH"))
                .setHeader(RedisConstants.MESSAGE, constant("This is hello message to Redis from Camel"))
                .to("spring-redis://localhost:6379")
                // key / value caching
                .setHeader(RedisConstants.COMMAND, constant("SET"))
                .setHeader(RedisConstants.KEY, constant("camelKey"))
                .setHeader(RedisConstants.VALUE, constant("camelValue"))
                .to("spring-redis://localhost:6379");

        // Camel Redis subscribe
        from("spring-redis://localhost:6379?command=SUBSCRIBE&channels=camelChannel")
                .routeId("Redis subscribe")
                .process(enrichExchangeBody)
                .bean(redisServiceBean, "getMessage(${body})")
                .to("mock:result");
	}

}
