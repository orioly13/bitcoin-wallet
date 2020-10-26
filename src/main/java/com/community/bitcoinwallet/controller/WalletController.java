package com.community.bitcoinwallet.controller;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.model.requests.AddWalletEntryRequest;
import com.community.bitcoinwallet.model.requests.BalanceRequest;
import com.community.bitcoinwallet.model.response.GeneralResponseData;
import com.community.bitcoinwallet.model.response.WalletEntryResponse;
import com.community.bitcoinwallet.service.WalletService;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WalletController extends AbstractController {

    WalletService walletService;

    @PostMapping(value = "/add-entry", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public GeneralResponseData addEntry(@RequestBody AddWalletEntryRequest entryRequest) {
        if (entryRequest == null || entryRequest.getDatetime() == null ||
            entryRequest.getAmount() == null) {
            throw new IllegalArgumentException("Request should not have null fields");
        }
        walletService.addEntry(
            new WalletEntry(entryRequest.getDatetime().toInstant(),
                DateAndAmountUtils.toBigDecimal(entryRequest.getAmount())));
        return OK_RESPONSE;
    }

    @PostMapping(value = "/balance", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public List<WalletEntryResponse> getBalance(@RequestBody BalanceRequest balanceRequest) {
        if (balanceRequest == null || balanceRequest.getFrom() == null ||
            balanceRequest.getTo() == null) {
            throw new IllegalArgumentException("Request should not have null fields");
        }
        List<WalletEntry> entries = walletService.getBalance(
            balanceRequest.getFrom().toInstant(),
            balanceRequest.getTo().toInstant());
        return entries.stream().map(walletEntry ->
            new WalletEntryResponse(
                DateAndAmountUtils.toUTCZonedDate(walletEntry.getDatetime()),
                walletEntry.getAmount().doubleValue()))
            .collect(Collectors.toList());
    }
}
