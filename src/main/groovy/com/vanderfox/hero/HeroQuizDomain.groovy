package com.vanderfox.hero

import grails.persistence.Entity

/**
 * Created by rvanderwerf on 6/15/16.
 */
@Entity
class HeroQuizDomain implements Serializable {
    int id
    String quizName
    static mapWith = "dynamodb"

}
