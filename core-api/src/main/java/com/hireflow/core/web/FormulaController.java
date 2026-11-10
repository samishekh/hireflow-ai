package com.hireflow.core.web;

import com.hireflow.core.service.ScoringFormulaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FormulaController {

    private final ScoringFormulaService formulas;

    public FormulaController(ScoringFormulaService formulas) {
        this.formulas = formulas;
    }

    @GetMapping("/api/formula")
    public FormulaResponse get() {
        return formulas.get();
    }

    @PutMapping("/api/formula")
    public FormulaResponse update(@Valid @RequestBody UpdateFormulaRequest request) {
        return formulas.update(request);
    }
}
