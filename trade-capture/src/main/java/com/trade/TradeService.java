package com.trade;

import org.springframework.stereotype.Service;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TradeService {

    @Autowired(required = false)
    private JmsTemplate jmsTemplate;

    @Autowired
    private TradeRepository tradeRepository;

    public Trade submitTrade(String name, int quantity, double price) {
        Trade trade = new Trade();
        trade.setName(name);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setTimestamp(LocalDateTime.now());
        trade.setStatus("RECEIVED");
        trade.setRuleResult("PENDING");
        trade.setFraudResult("PENDING");
        trade.setAckResult("PENDING");

        trade = tradeRepository.save(trade);
        String tradeId = trade.getId().toString();

        publishToQueue("TRADE.RECEIVED", tradeId + "," + name + "," + quantity + "," + price);

        return trade;
    }

    public List<Trade> getAllTrades() {
        return tradeRepository.findAll();
    }

    public Trade getTradeById(Long id) {
        return tradeRepository.findById(id).orElse(null);
    }

    public void processRuleResult(String message) {
        String[] parts = message.split(",");
        String tradeId = parts[0];
        String result = parts[1];

        System.out.println("TradeService.processRuleResult - TradeId: " + tradeId + ", Result: " + result);

        // Update trade record
        Trade trade = tradeRepository.findById(Long.parseLong(tradeId)).orElse(null);
        if (trade != null) {
            trade.setRuleResult(result);
            tradeRepository.save(trade);
            System.out.println("TradeService.processRuleResult - Updated trade " + tradeId + " in DB");
        }

        checkAndCombineResults(tradeId);
    }

    public void processFraudResult(String message) {
        String[] parts = message.split(",");
        String tradeId = parts[0];
        String result = parts[1];

        System.out.println("TradeService.processFraudResult - TradeId: " + tradeId + ", Result: " + result);

        // Update trade record
        Trade trade = tradeRepository.findById(Long.parseLong(tradeId)).orElse(null);
        if (trade != null) {
            trade.setFraudResult(result);
            tradeRepository.save(trade);
            System.out.println("TradeService.processFraudResult - Updated trade " + tradeId + " in DB");
        }

        checkAndCombineResults(tradeId);
    }

    private void checkAndCombineResults(String tradeId) {
        // Get the trade from database to check both results
        Trade trade = tradeRepository.findById(Long.parseLong(tradeId)).orElse(null);
        if (trade == null) {
            System.out.println("checkAndCombineResults - Trade " + tradeId + " not found in database!");
            return;
        }

        System.out.println("checkAndCombineResults - TradeId: " + tradeId + 
                           ", DB RuleResult: " + trade.getRuleResult() + 
                           ", DB FraudResult: " + trade.getFraudResult());

        // Check if both results are no longer PENDING (use database as source of truth)
        boolean hasRuleResult = !("PENDING".equals(trade.getRuleResult()));
        boolean hasFraudResult = !("PENDING".equals(trade.getFraudResult()));

        if (hasRuleResult && hasFraudResult) {
            String ruleResult = trade.getRuleResult();
            String fraudResult = trade.getFraudResult();
            String finalResult = ("APPROVE".equals(ruleResult) && "APPROVE".equals(fraudResult)) ? "ACK" : "NACK";

            System.out.println("checkAndCombineResults - Both results available for " + tradeId + 
                               ": Rule=" + ruleResult + ", Fraud=" + fraudResult + ", Final=" + finalResult);

            // Update trade record with final status
            trade.setStatus(finalResult);
            trade.setAckResult("SENT");
            tradeRepository.save(trade);
            System.out.println("checkAndCombineResults - Updated trade " + tradeId + " status to " + finalResult);

            // Publish final result to queue
            publishToQueue("TRADE.FINAL", tradeId + "," + finalResult);
            System.out.println("Trade " + tradeId + " final result: " + finalResult);
        } else {
            System.out.println("checkAndCombineResults - Waiting for results. TradeId: " + tradeId + 
                               " has Rule: " + hasRuleResult + ", has Fraud: " + hasFraudResult);
        }
    }

    private void publishToQueue(String queueName, String message) {
        try {
            if (jmsTemplate != null) {
                jmsTemplate.convertAndSend(queueName, message);
                System.out.println("Published to " + queueName + ": " + message);
            } else {
                System.out.println("JMS not available, simulating " + queueName + ": " + message);
            }
        } catch (Exception e) {
            System.out.println("Queue error, simulating " + queueName + ": " + message);
        }
    }
}