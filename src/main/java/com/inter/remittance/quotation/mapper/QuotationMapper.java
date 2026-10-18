package com.inter.remittance.quotation.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.inter.remittance.quotation.client.dto.BcbQuotationItem;
import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;
import com.inter.remittance.quotation.dto.BuyQuotation;

@Mapper
public interface QuotationMapper {

	BuyQuotation toBuyQuotation(BcbQuotationItem item);

	List<BuyQuotation> toBuyQuotations(List<BcbQuotationItem> items);

	default List<BuyQuotation> toBuyQuotations(BcbQuotationResponse response) {
		return response == null || response.value() == null ? List.of() : toBuyQuotations(response.value());
	}

}
