package org.vitalink.api;

import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class OpenApiController {
    @GetMapping(value="/api/v1/openapi.yaml",produces="application/yaml")
    public ResponseEntity<Resource> specification() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ClassPathResource("openapi.yaml"));
    }
}
