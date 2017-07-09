package com.vanderfox.hero.question

/**
 * Created by lfox on 6/16/16.
 */
class Question {
    String question
    String[] options
    String speechText
    String cardText
    int answer
    int index

    String getSpeechText() {
        speechText = ""
        speechText += speechText + question + "\n\n"
        int counter = 1
        for(String option: options) {
            speechText += counter++ + "\n\n\n" + option + "\n\n\n\n"
        }
        speechText
    }

    String getCardText() {
        cardText = ""
        cardText += cardText + question + "<br/>"
        int counter = 1
        for(String option: options) {
            cardText += counter++ + "\n\n\n" + option + "<br/>"
        }
        cardText
    }
}
