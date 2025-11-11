package com.trade;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FraudMessageListener {
    
    @Autowired
    private JmsTemplate jmsTemplate;
    
    @Autowired
    private FraudResultRepository repository;
    
    @JmsListener(destination = "TRADE.RECEIVED")
    public void processTrade(String message) {
        System.out.println("===== FRAUD SERVICE LISTENER TRIGGERED =====");
        System.out.println("Fraud Service received: " + message);
        
        try {
            String[] parts = message.split(",");
            String tradeId = parts[0];
            String name = parts[1];
            int quantity = Integer.parseInt(parts[2]);
            double price = Double.parseDouble(parts[3]);
            
            System.out.println("Fraud Service - Parsed: TradeId=" + tradeId + ", Name=" + name + ", Qty=" + quantity + ", Price=" + price);
            
            String result = checkFraud(name, quantity, price);
            int riskScore = calculateRiskScore(quantity, price);
            String reason = getFraudReason(quantity, price);
            
            System.out.println("Fraud Service - CheckFraud result: " + result + ", RiskScore: " + riskScore);
            
            // Save to database
            FraudResult fraudResult = new FraudResult();
            fraudResult.setTradeId(tradeId);
            fraudResult.setResult(result);
            fraudResult.setRiskScore(riskScore);
            fraudResult.setReason(reason);
            fraudResult.setTimestamp(java.time.LocalDateTime.now());
            repository.save(fraudResult);
            System.out.println("Fraud Service - Saved to database: " + tradeId);
            
            jmsTemplate.convertAndSend("FRAUD.RESULT", tradeId + "," + result);
            System.out.println("Fraud Service - Published to FRAUD.RESULT: " + tradeId + "," + result);
            System.out.println("Fraud check for " + tradeId + ": " + result);
        } catch (Exception e) {
            System.out.println("===== FRAUD SERVICE ERROR =====");
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("===== FRAUD SERVICE LISTENER COMPLETE =====");
    }
    
    private String checkFraud(String name, int quantity, double price) {
        // DUMMY: Always approve for demo purposes
        return "APPROVE";
    }
    
    private int calculateRiskScore(int quantity, double price) {
        // DUMMY: Always low risk score
        return 10;
    }
    
    private String getFraudReason(int quantity, double price) {
        // DUMMY: Always low risk
        return "Dummy fraud check - always approved";
    }
}