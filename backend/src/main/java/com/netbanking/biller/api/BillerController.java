package com.netbanking.biller.api;
import com.netbanking.biller.service.BillerService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/billers")
public class BillerController { private final BillerService service; public BillerController(BillerService service) { this.service = service; } @GetMapping public List<BillerResponse> list() { return service.listActive(); } }
