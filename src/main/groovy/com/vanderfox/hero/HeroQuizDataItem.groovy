package com.vanderfox.hero;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

/**
 * Model representing an item of the ScoreKeeperUserData table in DynamoDB for the ScoreKeeper
 * skill.
 */
@DynamoDBTable(tableName = "HeroQuiz")
public class HeroQuizDataItem {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(HeroQuizDataItem.class);

    private String id;

    private HeroQuizQuestionData questionData;

    @DynamoDBHashKey(attributeName = "id")
    public int getId() {
        log.info ("get item index is:  " + id)
        id;
    }

    public void setId(int id) {
        this.id = id;
        log.info ("set item index is:  " + id)
    }

    @DynamoDBAttribute(attributeName = "question")
    @DynamoDBMarshalling(marshallerClass = HeroQuizGameDataMarshaller.class)
    public HeroQuizQuestionData getQuestion() {
        return questionData;
    }

    public static class HeroQuizGameDataMarshaller implements
            DynamoDBMarshaller<HeroQuizQuestionData> {

        @Override
        public String marshall(HeroQuizQuestionData questionData) {
            try {
                return OBJECT_MAPPER.writeValueAsString(questionData);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to marshall game data", e);
            }
        }

        @Override
        public HeroQuizQuestionData unmarshall(Class<HeroQuizQuestionData> clazz, String value) {
            try {
                return OBJECT_MAPPER.readValue(value, new TypeReference<HeroQuizQuestionData>() {
                });
            } catch (Exception e) {
                throw new IllegalStateException("Unable to unmarshall game data value", e);
            }
        }
    }
}
