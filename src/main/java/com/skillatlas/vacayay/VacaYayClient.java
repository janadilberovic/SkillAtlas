package com.skillatlas.vacayay;

import java.util.List;

import com.skillatlas.vacayay.dto.VacaYayEmployee;

/**
 * The old system, as far as SkillAtlas is concerned. The only reason this is an interface is that
 * {@link HttpVacaYayClient} is then the single file that knows VacaYAY lives at a URL — every
 * import test swaps this out and runs without a .NET process anywhere on the machine.
 */
public interface VacaYayClient {

    /** Every employee the old system will hand over, in its own order. */
    List<VacaYayEmployee> fetchEmployees();
}
