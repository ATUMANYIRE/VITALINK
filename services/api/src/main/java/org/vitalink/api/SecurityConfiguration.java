package org.vitalink.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http,AuthService auth,ObjectMapper json) throws Exception {
        return http.csrf(c->c.disable()).cors(c->c.disable())
            .httpBasic(c->c.disable()).formLogin(c->c.disable()).logout(c->c.disable())
            .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/health","/actuator/health/**","/api/v1/openapi.yaml").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/sync/referrals").hasAnyRole("HEALTH_WORKER","ADMIN")
                .requestMatchers(HttpMethod.POST,"/api/v1/referrals/*/acknowledge","/api/v1/referrals/*/close").hasAnyRole("DOCTOR","ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e->e
                .authenticationEntryPoint((req,res,error)->writeProblem(json,res,401,"UNAUTHENTICATED","Sign in with valid credentials.",req.getRequestURI()))
                .accessDeniedHandler((req,res,error)->writeProblem(json,res,403,"FORBIDDEN","This action is not authorized.",req.getRequestURI())))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    res.setHeader("Cache-Control","no-store");
                    res.setHeader("Pragma","no-cache");
                    if(req.getContentLengthLong()>65536) {
                        writeProblem(json,res,413,"PAYLOAD_TOO_LARGE","Request body exceeds the limit.",req.getRequestURI()); return;
                    }
                    String header=req.getHeader("Authorization");
                    if(header!=null && !(req.getMethod().equals("POST") && req.getRequestURI().equals("/api/v1/auth/login"))) {
                        try {
                            if(!header.startsWith("Bearer ")) throw ApiProblem.unauthorized();
                            var user=auth.authenticate(header.substring(7));
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,
                                List.of(new SimpleGrantedAuthority("ROLE_"+user.role().name()))));
                        } catch(ApiProblem error) {
                            writeProblem(json,res,error.status(),error.code(),error.getMessage(),req.getRequestURI()); return;
                        } catch(org.springframework.dao.DataAccessException error) {
                            writeProblem(json,res,503,"SERVICE_UNAVAILABLE","Authentication service temporarily unavailable.",req.getRequestURI()); return;
                        }
                    }
                    if (req.getMethod().equals("POST")) {
                        byte[] body=req.getInputStream().readNBytes(65537);
                        if(body.length>65536) {
                            writeProblem(json,res,413,"PAYLOAD_TOO_LARGE","Request body exceeds the limit.",req.getRequestURI()); return;
                        }
                        var input=new ByteArrayInputStream(body);
                        chain.doFilter(new HttpServletRequestWrapper(req) {
                            @Override public ServletInputStream getInputStream() {
                                return new ServletInputStream() {
                                    @Override public int read() { return input.read(); }
                                    @Override public int read(byte[] target,int offset,int length) { return input.read(target,offset,length); }
                                    @Override public boolean isFinished() { return input.available()==0; }
                                    @Override public boolean isReady() { return true; }
                                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Async body reading is unavailable."); }
                                };
                            }
                        },res);
                    } else chain.doFilter(req,res);
                }
            },UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void writeProblem(ObjectMapper json,HttpServletResponse response,int status,String code,String detail,String path) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(),ProblemHandler.response(status,code,detail,path).getBody());
    }
}
