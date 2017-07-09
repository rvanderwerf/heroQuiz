package com.vanderfox.hero

import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.speechlet.interfaces.display.directive.RenderTemplateDirective
import com.amazon.speech.speechlet.interfaces.display.element.ImageInstance
import com.amazon.speech.speechlet.interfaces.display.element.RichText
import com.amazon.speech.speechlet.interfaces.display.template.BodyTemplate1
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.vanderfox.hero.question.Question
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient

/**
 * This app shows how to connect to hero with Spring Social, Groovy, and Alexa.
 * @author Lee Fox and Ryan Vanderwerf
 */
@CompileStatic
class HeroSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(HeroSpeechlet.class)

    @Override
    void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())

        LinkedHashMap<String, Question> askedQuestions = new LinkedHashMap()
        session.setAttribute("askedQuestions", askedQuestions)
        session.setAttribute("questionCounter", 10)
        session.setAttribute("score", 0)
        initializeComponents(session)
    }

    @Override
    SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        getWelcomeResponse(session)
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse(Session session) {
        log.info("Welcome message")
        int numberOfQuestions = Integer.parseInt((String) session.getAttribute("questionCounter"))
        String speechText = "Welcome to Hero Quiz.  I'm going to ask you " + numberOfQuestions + " questions to test your comic book knowledge.  Say repeat question at any time if you need to hear a question again, or say help if you need some help.  Let's get started:   \n\n"
        String cardText = "Welcome to Hero Quiz.  I'm going to ask you " + numberOfQuestions + " questions to test your comic book knowledge.  Say <b>repeat question</b> at any time if you need to hear a question again, or say <b>help</b> if you need some help.  Let's get started:   <br/><br/>"
        Question question = getRandomUnaskedQuestion(session)
        session.setAttribute("lastQuestionAsked", question)
        speechText += question.getSpeechText()
        cardText += question.getCardText()
        askResponse(cardText, speechText)
    }

    @Override
    SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())

        Intent intent = request.getIntent()
        String intentName = (intent != null) ? intent.getName() : null
        log.debug("Intent = " + intentName)
        switch (intentName) {
            case "AnswerIntent":
                getAnswer(intent.getSlot("Answer"), session)
                break
            case "DontKnowIntent":
                processAnswer(session, 5)
                break
            case "AMAZON.HelpIntent":
                getHelpResponse(session)
                break
            case "AMAZON.CancelIntent":
                sayGoodbye()
                break
            case "AMAZON.RepeatIntent":
                repeatQuestion(session)
                break
            case "AMAZON.StopIntent":
                sayGoodbye()
                break
            default:
                didNotUnderstand()
                break
        }
    }

    @Override
    void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse sayGoodbye() {
        String speechText = "OK.  I'm going to stop the game now."
        tellResponse(speechText, speechText)
    }

    private Question getRandomUnaskedQuestion(Session session) {
        LinkedHashMap<String, Question> askedQuestions = (LinkedHashMap) session.getAttribute("askedQuestions")
        Question question = getRandomQuestion(session)
        while(askedQuestions.get(question.getQuestion()) != null) {
            question = getRandomQuestion(session)
        }
        askedQuestions.put(question.getQuestion(), question)
        session.setAttribute("askedQuestions", askedQuestions)
        question
    }

    private Question getRandomQuestion(Session session) {
        int tableRowCount = Integer.parseInt((String) session.getAttribute("tableRowCount"))
        int questionIndex = (new Random().nextInt() % tableRowCount).abs()
        log.info("The question index is:  " + questionIndex)
        Question question = getQuestion(questionIndex)
        question
    }

    private Question getQuestion(int questionIndex) {


        DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient())
        Table table = dynamoDB.getTable("HeroQuiz")
        Item item = table.getItem("Id", questionIndex)
        def questionText = item.getString("Question")
        def questionAnswer = item.getInt("answer")
        def options = new String[4]
        options[0] = item.getString("option1")
        options[1] = item.getString("option2")
        options[2] = item.getString("option3")
        options[3] = item.getString("option4")
        Question question = new Question()
        question.setQuestion(questionText)
        question.setOptions(options)
        question.setAnswer(questionAnswer)
        question.setIndex(questionIndex)
        log.info("question retrieved index:  " + question.getIndex())
        log.info("question retrieved text:  " + question.getQuestion())
        log.info("question retrieved correct:  " + question.getAnswer())
        log.info("question retrieved number of options:  " + question.getOptions().length)
        question
    }

    private SpeechletResponse askResponse(String cardText, String speechText) {

        RenderTemplateDirective renderTemplateDirective = buildBodyTemplate1(cardText)

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse response = new SpeechletResponse()
        ArrayList directives = new ArrayList()
        directives.add(renderTemplateDirective)
        response.setDirectives(directives)

        response.setNullableShouldEndSession(false)
        response.setOutputSpeech(speech)
        response.setReprompt(reprompt)
        response

    }

    private RenderTemplateDirective buildBodyTemplate1(String cardText) {
        BodyTemplate1 template = new BodyTemplate1()
        template.setTitle("Hero Quiz")
        BodyTemplate1.TextContent textContent = new BodyTemplate1.TextContent()
        RichText richText = new RichText()
        richText.text = cardText
        textContent.setPrimaryText(richText)
        template.setTextContent(textContent)
        com.amazon.speech.speechlet.interfaces.display.element.Image backgroundImage = new com.amazon.speech.speechlet.interfaces.display.element.Image()
        ImageInstance imageInstance = new ImageInstance()
        imageInstance.setUrl("https://s-media-cache-ak0.pinimg.com/originals/e4/30/78/e43078050e9a8d5bc2f8a1ed09a77227.png")
        ArrayList<ImageInstance> imageInstances = new ArrayList()
        imageInstances.add(imageInstance)
        backgroundImage.setSources(imageInstances)
        template.setBackgroundImage(backgroundImage)
        RenderTemplateDirective renderTemplateDirective = new RenderTemplateDirective()
        renderTemplateDirective.setTemplate(template)
        renderTemplateDirective
    }

    private SpeechletResponse tellResponse(String cardText, String speechText) {
        RenderTemplateDirective renderTemplateDirective = buildBodyTemplate1(cardText)

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse response = new SpeechletResponse()
        ArrayList directives = new ArrayList()
        directives.add(renderTemplateDirective)
        response.setDirectives(directives)
        response.setNullableShouldEndSession(true)
        response.setOutputSpeech(speech)
        response.setReprompt(reprompt)
        response


    }

    private SpeechletResponse askResponseFancy(String cardText, String speechText, String fileUrl) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard()
        card.setTitle("Hero Quiz")
        card.setContent(cardText)

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)
        log.info("making welcome audio")
        SsmlOutputSpeech fancySpeech = new SsmlOutputSpeech()
        fancySpeech.ssml = "<speak><audio src=\"${fileUrl}\"/> ${speechText}</speak>"
        log.info("finished welcome audio")
        // Create reprompt
        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(fancySpeech)

        SpeechletResponse.newAskResponse(fancySpeech, reprompt, card)
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse repeatQuestion(final Session session) {
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        String speechText = ""

        speechText += question.getSpeechText()
        askResponse(speechText, speechText)

    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getAnswer(Slot query, final Session session) {

        int guessedAnswer = Integer.parseInt(query.getValue())
        log.info("Guessed answer is:  " + query.getValue())

        return processAnswer(session, guessedAnswer)
    }

    private SpeechletResponse processAnswer(Session session, int guessedAnswer) {
        def speechText
        def cardText
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        def answer = question.getAnswer()
        log.info("correct answer is:  " + answer)
        log.info("question was:  " + question.getQuestion())

        int questionCounter = decrementQuestionCounter(session)

        if (guessedAnswer == answer) {
            speechText = "You got it right.\n\n"
            cardText = "You got it right.<br/><br/>"
            int score = (Integer) session.getAttribute("score")
            score++
            session.setAttribute("score", score)
            questionMetricsCorrect(question.getIndex())
        } else {
            speechText = "You got it wrong.\n\n"
            cardText = "You got it wrong.<br/><br/>"
            questionMetricsWrong(question.getIndex())
        }

        log.info("questionCounter:  " + questionCounter)

        if (questionCounter > 0) {
            session.setAttribute("state", "askQuestion")
            question = getRandomUnaskedQuestion(session)
            session.setAttribute("lastQuestionAsked", question)
            speechText += question.getSpeechText()
            cardText += question.getCardText()
            return askResponse(cardText, speechText)
        } else {
            int score = (Integer) session.getAttribute("score")
            speechText += "\n\nYou answered ${score} questions correctly.\n\nThank you for playing."
            cardText += "You answered ${score} questions correctly.<br/><br/>Thank you for playing."
            userMetrics(session.getUser().userId, score)
            return tellResponse(cardText, speechText)
        }
    }

    private void questionMetricsCorrect(int questionIndex) {
        questionMetrics(questionIndex, true)
    }

    private void questionMetricsWrong(int questionIndex) {
        questionMetrics(questionIndex, false)
    }

    private void questionMetrics(int questionIndex, boolean correct) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("HeroQuizMetrics")
        Item item = table.getItem("id", questionIndex)
        int askedCount = 0
        int correctCount = 0
        if (item != null) {
            askedCount = item.getInt("asked")
            correctCount = item.getInt("correct")
        }
        askedCount++
        if (correct) {
            correctCount++
        }
        Item newItem = new Item()
        newItem.withInt("id", questionIndex)
        newItem.withInt("asked", askedCount)
        newItem.withInt("correct", correctCount)
        table.putItem(newItem)
    }

    private void userMetrics(String userId, int score) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("HeroQuizUserMetrics")
        Item item = table.getItem("id", userId)
        int timesPlayed = 0
        int correctCount = 0
        if (item != null) {
            timesPlayed = item.getInt("timesPlayed")
            correctCount = item.getInt("lifeTimeCorrect")
        }
        timesPlayed++
        correctCount += score
        Item newItem = new Item()
        newItem.withString("id", userId)
        newItem.withInt("timesPlayed", timesPlayed)
        newItem.withInt("lifeTimeCorrect", correctCount)
        table.putItem(newItem)
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse(Session session) {
        String speechText = ""
        speechText = "You can say stop or cancel to end the game at any time.  If you need a question repeated, say repeat question."
        askResponse(speechText, speechText)
    }

    private SpeechletResponse didNotUnderstand() {
        String speechText = "I'm sorry.  I didn't understand what you said.  Say help me for help."
        askResponse(speechText, speechText)
    }

    private int decrementQuestionCounter(Session session) {
        int questionCounter = (int) session.getAttribute("questionCounter")
        questionCounter--
        session.setAttribute("questionCounter", questionCounter)
        questionCounter

    }

    /**
     * Initializes the instance components if needed.
     */
    private void initializeComponents(Session session) {
        AmazonDynamoDBClient amazonDynamoDBClient
        amazonDynamoDBClient = new AmazonDynamoDBClient()
        ScanRequest req = new ScanRequest()
        req.setTableName("HeroQuiz")
        ScanResult result = amazonDynamoDBClient.scan(req)
        List quizItems = result.items
        int tableRowCount = quizItems.size()
        session.setAttribute("tableRowCount", Integer.toString(tableRowCount))
        log.info("This many rows in the table:  " + tableRowCount)
    }
}
