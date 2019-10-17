package com.sanxin.cloud.app.api.common;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;


/**
 * 短信工具类
 *
 * @author arno
 */
@Component("SnsUtils")
public class SnsUtils {

    private Map<String, MessageAttributeValue> smsAttributes;

    private static String AWS_ACCESS_KEYID;
    private static String AWS_SECRET_KEY;


    @Value("${sns.AWS_ACCESS_KEYID}")
    public void setKeyId(String keyid) {
        AWS_ACCESS_KEYID = keyid;
    }

    @Value("${sns.AWS_SECRET_KEY}")
    public void setKey(String key) {
        AWS_SECRET_KEY = key;
    }

    public boolean send2SNS(String phoneNumber, String message) {
        if (phoneNumber.contains("_")) {
            phoneNumber = StringUtils.replace(phoneNumber, "_", "");
        }
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+" + phoneNumber;
        }
        PublishResult result = sendSMSMessage(phoneNumber, message, getDefaultSMSAttributes());
        if (result != null && !StringUtils.isEmpty(result.getMessageId())) return true;
        return false;
    }


    public Map<String, MessageAttributeValue> getDefaultSMSAttributes() {
        if (smsAttributes == null) {
            smsAttributes = new HashMap<>();
            smsAttributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                    .withStringValue("1")
                    .withDataType("String"));
            smsAttributes.put("AWS.SNS.SMS.MaxPrice", new MessageAttributeValue()
                    .withStringValue("0.05")
                    .withDataType("Number"));
            smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
                    .withStringValue("Transactional")
                    .withDataType("String"));
        }
        return smsAttributes;
    }


    public PublishResult sendSMSMessage(String phoneNumber, String message, Map<String, MessageAttributeValue> smsAttributes) {
        AWSCredentials awsCredentials = new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return AWS_ACCESS_KEYID;// 带有发短信权限的 IAM 的 ACCESS_KEY
            }

            @Override
            public String getAWSSecretKey() {
                return AWS_SECRET_KEY; // 带有发短信权限的 IAM 的 SECRET_KEY
            }

        };
        AWSCredentialsProvider provider = new AWSCredentialsProvider() {
            @Override
            public AWSCredentials getCredentials() {
                return awsCredentials;
            }

            @Override
            public void refresh() {
            }
        };
        AmazonSNS amazonSNS = null;
        try {
            //设置aws区域
            amazonSNS = AmazonSNSClientBuilder.standard().withCredentials(provider).withRegion("us-east-1").build();
        } catch (Exception e) {

        }
        return amazonSNS.publish(
                new PublishRequest()
                        .withMessage(message)
                        .withPhoneNumber(phoneNumber)
                        .withMessageAttributes(smsAttributes)
        );
    }
}


