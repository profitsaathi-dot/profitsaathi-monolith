package com.profitsaathi.seller.whatsapp;

import lombok.Data;

@Data
public class SendMessageRequest {
    /** Indian mobile in E.164 form without +, e.g. "919999999999". */
    private String to;
    private String text;
}
