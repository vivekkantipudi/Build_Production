package com.gateway.services;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class JobService {
    private static final String REDIS_HOST = System.getenv("REDIS_URL") != null ? "redis_gateway" : "localhost";
    private static final JedisPool pool = new JedisPool(new JedisPoolConfig(), REDIS_HOST, 6379);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void enqueueJob(String jobType, Object dataObject) {
        try (Jedis jedis = pool.getResource()) {
            String dataJson = (dataObject instanceof String) ? (String) dataObject : mapper.writeValueAsString(dataObject);
            JobPayload payload = new JobPayload(jobType, dataJson);
            jedis.rpush("job_queue", mapper.writeValueAsString(payload));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static JobPayload waitForJob() {
        try (Jedis jedis = pool.getResource()) {
            List<String> result = jedis.blpop(0, "job_queue");
            if (result != null && result.size() > 1) {
                return mapper.readValue(result.get(1), JobPayload.class);
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
            try { Thread.sleep(5000); } catch (Exception ignored) {}
        }
        return null;
    }
}