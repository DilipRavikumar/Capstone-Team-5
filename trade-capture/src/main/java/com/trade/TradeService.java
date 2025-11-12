package com.trade;

import org.springframework.stereotype.Service;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TradeService {

    @Autowired(required = false)
    private JmsTemplate jmsTemplate;

    @Autowired
    private TradeRepository tradeRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

        trade = tradeRepository.saveAndFlush(trade);
        entityManager.clear();

        String tradeId = trade.getId().toString();

        publishToQueue("TRADE.RULES", tradeId + "," + name + "," + quantity + "," + price);
        publishToQueue("TRADE.FRAUD", tradeId + "," + name + "," + quantity + "," + price);

        return trade;
    }

    public List<Trade> getAllTrades() {
        return tradeRepository.findAll();
    }

    public Trade getTradeById(Long id) {
        return tradeRepository.findById(id).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRuleResult(String message) {
        String[] parts = message.split(",");
        String tradeId = parts[0];
        String result = parts[1];

        System.out.println("TradeService.processRuleResult - TradeId: " + tradeId + ", Result: " + result);

        Trade trade = tradeRepository.findByIdForUpdate(Long.parseLong(tradeId)).orElse(null);
        if (trade != null) {
            trade.setRuleResult(result);
            System.out.println(trade.getId() + " " + trade.getRuleResult() + " " + result + " Rulesss");
            tradeRepository.saveAndFlush(trade);
            entityManager.clear();
            System.out.println("TradeService.processRuleResult - Updated trade " + tradeId + " in DB");
        }

        checkAndCombineResults(tradeId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFraudResult(String message) {
        String[] parts = message.split(",");
        String tradeId = parts[0];
        String result = parts[1];

        System.out.println("TradeService.processFraudResult - TradeId: " + tradeId + ", Result: " + result);

        Trade trade = tradeRepository.findByIdForUpdate(Long.parseLong(tradeId)).orElse(null);
        if (trade != null) {
            trade.setFraudResult(result);
            System.out.println(trade.getId() + " " + trade.getFraudResult() + " " + result + " Frauddd");
            tradeRepository.saveAndFlush(trade);
            entityManager.clear();
            System.out.println("TradeService.processFraudResult - Updated trade " + tradeId + " in DB");
        }

        checkAndCombineResults(tradeId);
    }

    private void checkAndCombineResults(String tradeId) {
        // Retry loop for freshness in highly concurrent scenarios (optional for prod, good for demo)
        for (int i = 0; i < 10; i++) {
            entityManager.clear();
            Trade trade = tradeRepository.findById(Long.parseLong(tradeId)).orElse(null);
            if (trade == null) {
                System.out.println("checkAndCombineResults - Trade " + tradeId + " not found in database!");
                return;
            }

            System.out.println("checkAndCombineResults - TradeId: " + tradeId +
                    ", DB RuleResult: " + trade.getRuleResult() +
                    ", DB FraudResult: " + trade.getFraudResult());

            boolean hasRuleResult = !("PENDING".equals(trade.getRuleResult()));
            boolean hasFraudResult = !("PENDING".equals(trade.getFraudResult()));

            if (hasRuleResult && hasFraudResult) {
                String ruleResult = trade.getRuleResult();
                String fraudResult = trade.getFraudResult();
                String finalResult = ("APPROVE".equals(ruleResult) && "APPROVE".equals(fraudResult)) ? "ACK" : "NACK";

                System.out.println("checkAndCombineResults - Both results available for " + tradeId +
                        ": Rule=" + ruleResult + ", Fraud=" + fraudResult + ", Final=" + finalResult);

                trade.setStatus(finalResult);
                trade.setAckResult("SENT");
                tradeRepository.saveAndFlush(trade);
                entityManager.clear();
                System.out.println("checkAndCombineResults - Updated trade " + tradeId + " status to " + finalResult);

                publishToQueue("TRADE.FINAL", tradeId + "," + finalResult);
                System.out.println("Trade " + tradeId + " final result: " + finalResult);

                break;
            } else {
                System.out.println("checkAndCombineResults - Waiting for results. TradeId: " + tradeId +
                        " has Rule: " + hasRuleResult + ", has Fraud: " + hasFraudResult);
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
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
