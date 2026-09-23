package com.netbanking.beneficiary.api;
import com.netbanking.beneficiary.service.BeneficiaryService;
import com.netbanking.security.SecurityContextHelper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {
    private final BeneficiaryService service; public BeneficiaryController(BeneficiaryService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public BeneficiaryResponse create(@Valid @RequestBody BeneficiaryRequest request) { return service.create(SecurityContextHelper.currentUserId(), request); }
    @GetMapping public List<BeneficiaryResponse> list() { return service.list(SecurityContextHelper.currentUserId()); }
    @PostMapping("/{beneficiaryId}/activate") public BeneficiaryResponse activate(@PathVariable Long beneficiaryId) { return service.activate(SecurityContextHelper.currentUserId(), beneficiaryId); }
    @DeleteMapping("/{beneficiaryId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void disable(@PathVariable Long beneficiaryId) { service.disable(SecurityContextHelper.currentUserId(), beneficiaryId); }
}
