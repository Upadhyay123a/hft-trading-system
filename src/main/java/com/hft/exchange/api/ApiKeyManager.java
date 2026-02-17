package com.hft.exchange.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure API Key Management for Exchange Integration
 * Handles authentication, signing, and secure key storage
 */
public class ApiKeyManager {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyManager.class);
    private static final String CONFIG_FILE = "exchange-api-keys.properties";
    
    // Exchange API credentials
    private final ConcurrentHashMap<String, ExchangeCredentials> credentials = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static volatile ApiKeyManager instance;
    
    private ApiKeyManager() {
        loadCredentials();
    }
    
    public static ApiKeyManager getInstance() {
        if (instance == null) {
            synchronized (ApiKeyManager.class) {
                if (instance == null) {
                    instance = new ApiKeyManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Load API credentials from secure properties file
     */
    private void loadCredentials() {
        Properties props = new Properties();
        
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            props.load(input);
            
            // Load Binance credentials
            String binanceKey = props.getProperty("binance.api.key");
            String binanceSecret = props.getProperty("binance.api.secret");
            if (binanceKey != null && binanceSecret != null) {
                credentials.put("binance", new ExchangeCredentials(binanceKey, binanceSecret));
                logger.info("Loaded Binance API credentials");
            }
            
            // Load Coinbase credentials
            String coinbaseKey = props.getProperty("coinbase.api.key");
            String coinbaseSecret = props.getProperty("coinbase.api.secret");
            String coinbasePassphrase = props.getProperty("coinbase.api.passphrase");
            if (coinbaseKey != null && coinbaseSecret != null) {
                credentials.put("coinbase", new ExchangeCredentials(coinbaseKey, coinbaseSecret, coinbasePassphrase));
                logger.info("Loaded Coinbase API credentials");
            }
            
        } catch (IOException e) {
            logger.warn("Could not load API credentials file: {}", e.getMessage());
            logger.info("Create {} file with your API keys", CONFIG_FILE);
        }
    }
    
    /**
     * Get credentials for specific exchange
     */
    public ExchangeCredentials getCredentials(String exchange) {
        return credentials.get(exchange.toLowerCase());
    }
    
    /**
     * Add credentials programmatically (for testing)
     */
    public void addCredentials(String exchange, String apiKey, String secretKey) {
        credentials.put(exchange.toLowerCase(), new ExchangeCredentials(apiKey, secretKey));
        logger.info("Added {} API credentials", exchange);
    }
    
    /**
     * Add Coinbase credentials with passphrase
     */
    public void addCredentials(String exchange, String apiKey, String secretKey, String passphrase) {
        credentials.put(exchange.toLowerCase(), new ExchangeCredentials(apiKey, secretKey, passphrase));
        logger.info("Added {} API credentials with passphrase", exchange);
    }
    
    /**
     * Generate HMAC SHA256 signature for API requests
     */
    public String generateSignature(String exchange, String message) {
        ExchangeCredentials cred = credentials.get(exchange.toLowerCase());
        if (cred == null) {
            throw new IllegalArgumentException("No credentials found for exchange: " + exchange);
        }
        
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(cred.secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            
            byte[] signatureBytes = sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Failed to generate signature for {}", exchange, e);
            throw new RuntimeException("Signature generation failed", e);
        }
    }
    
    /**
     * Check if exchange is configured
     */
    public boolean isExchangeConfigured(String exchange) {
        return credentials.containsKey(exchange.toLowerCase());
    }
    
    /**
     * Get list of configured exchanges
     */
    public String[] getConfiguredExchanges() {
        return credentials.keySet().toArray(new String[0]);
    }
    
    /**
     * Exchange credentials container
     */
    public static class ExchangeCredentials {
        public final String apiKey;
        public final String secretKey;
        public final String passphrase; // For Coinbase
        
        public ExchangeCredentials(String apiKey, String secretKey) {
            this.apiKey = apiKey;
            this.secretKey = secretKey;
            this.passphrase = null;
        }
        
        public ExchangeCredentials(String apiKey, String secretKey, String passphrase) {
            this.apiKey = apiKey;
            this.secretKey = secretKey;
            this.passphrase = passphrase;
        }
        
        public boolean hasPassphrase() {
            return passphrase != null && !passphrase.isEmpty();
        }
    }
}
