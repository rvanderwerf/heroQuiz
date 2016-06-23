package com.vanderfox.hero;

import com.amazon.speech.speechlet.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

/**
 * Contains the methods to interact with the persistence layer for ScoreKeeper in DynamoDB.
 */
public class HeroQuizDao {
    private final HeroQuizDynamoDbClient dynamoDbClient;
    private static final Logger log = LoggerFactory.getLogger(HeroQuizDao.class);

    public HeroQuizDao(HeroQuizDynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public HeroQuizDataItem getQuestion(int id) {
        HeroQuizDataItem item = new HeroQuizDataItem();
        item.setId(id);
        log.info ("item index is:  " + id)

        item = dynamoDbClient.loadItem(item);

        if (item == null) {
            return null;
        }

        item
    }
}
