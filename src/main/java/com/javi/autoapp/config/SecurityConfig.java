package com.javi.autoapp.config;

import com.javi.autoapp.security.XssStrictHttpFirewall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    public static final String AUTO_APP_USERNAME = System.getProperty("AUTO_APP_USERNAME");
    public static final String AUTO_APP_PASSPHRASE = System.getProperty("AUTO_APP_PASSWORD");

    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .passwordEncoder(passwordEncoder)
                .withUser(AUTO_APP_USERNAME)
                .password(passwordEncoder.encode(AUTO_APP_PASSPHRASE)).roles("USER");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
        .antMatchers("/login")
            .permitAll()
        .antMatchers("/actuator/health")
            .permitAll()
        .antMatchers("/graphiql")
            .hasAnyRole("USER").anyRequest().authenticated()
        .and()
            .formLogin()
            .loginPage("/login")
            .loginProcessingUrl("/perform_login")
            .defaultSuccessUrl("/graphiql",true)
        .and()
             .logout()
             .logoutUrl("/perform_logout")
             .deleteCookies("JSESSIONID")
        .and()
            .csrf()
            .disable();
    }

    @Override
    public final void configure(final WebSecurity web) throws Exception
    {
        super.configure(web);
        web.httpFirewall(new XssStrictHttpFirewall());
    }
}
