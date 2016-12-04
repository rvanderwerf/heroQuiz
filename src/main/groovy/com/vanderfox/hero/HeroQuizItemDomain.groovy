package com.vanderfox.hero

import grails.persistence.Entity

/**
 * Created by rvanderwerf on 6/15/16.
 */
@Entity
class HeroQuizItemDomain implements Serializable{
    HeroQuizDomain quiz
    String question
    String domain
    int id
    static mapWith = "dynamodb"

}
