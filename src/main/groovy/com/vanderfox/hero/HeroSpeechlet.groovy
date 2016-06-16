package com.vanderfox.hero

import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import com.vanderfox.hero.question.Question
import com.vanderfox.hero.user.User
import groovy.transform.CompileStatic
import org.slf4j.Logger;
import org.slf4j.LoggerFactory
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;




/**
 * Created by Lee Fox and Ryan Vanderwerf on 3/18/16.
 */
/**
 * This app shows how to connect to hero with Spring Social, Groovy, and Alexa.
 */
@CompileStatic
public class HeroSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(HeroSpeechlet.class);
    private AmazonDynamoDBClient amazonDynamoDBClient;
    int tableRowCount = 0

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        session.setAttribute("playerList", new ArrayList<User>())
        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        initializeComponents(session)

        getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : null;
        Slot query = intent.getSlot("Answer")
        Slot count = intent.getSlot("Count")

        switch (intentName) {
            case "QuizIntent":
                getHeroQuestion(session)
                break
            case "PlayerNameIntent":
                setPlayerName(query, session)
                break
            case "AnswerIntent":
                getHeroAnswer(query, session)
                break
            case "QuestionCountIntent":
                setQuestionCount(count, session)
                break
            case "AMAZON.HelpIntent":
                getHelpResponse()
                break
            default:
                getHelpResponse()
                break
        }

    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        // any cleanup logic goes here
    }

    /**
     * Creates and returns a {@code SpeechletResponse} with a welcome message.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechText = "Welcome to Hero Quiz.  Please tell me the first players name.";

        askResponseFancy(speechText, speechText, "https://s3.amazonaws.com/vanderfox-sounds/test.mp3")
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHeroQuestion(final Session session) {
        getHeroQuestion(session, "")
    }

        /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHeroQuestion(final Session session, String speechText) {
        Question question = getRandomUnaskedQuestion(session)
        session.setAttribute("lastQuestionAsked", question)

        speechText += "\n"
        speechText += question.getText()
        askResponse(speechText, speechText)

    }

    private Question getRandomUnaskedQuestion(Session session) {
        LinkedHashMap<String, Question> askedQuestions = (LinkedHashMap) session.getAttribute("askedQuestions")
        Question question = getRandomQuestion()
        while(askedQuestions.get(question.getText()) != null) {
            question = getRandomQuestion()
        }
        askedQuestions.put(question.getText(), question)
        session.setAttribute("askedQuestions", askedQuestions)
        question
    }

    private Question getRandomQuestion() {
        int questionIndex = (new Random().nextInt() % tableRowCount).abs()
        log.info("The question index is:  " + questionIndex)
        Question question = getQuestion(questionIndex)
        question
    }

    private Question getQuestion(int questionIndex) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient());
        Table table = dynamoDB.getTable("HeroQuiz");
        Item item = table.getItem("id", questionIndex);
        def questionText = item.getString("question")
        Question question = new Question()
        question.setText(questionText)
        question.setIndex(questionIndex)
        question
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse setQuestionCount(Slot count, final Session session) {
        session.setAttribute("questionCounter", Integer.parseInt(count.getValue()))
        session.setAttribute("numberOfQuestions", Integer.parseInt(count.getValue()))

        int numberOfQuestions = getNumberOfQuestions(session)
        def speechText = "OK.  Got it.  Let’s get started.";

        ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
        User player = playerList.get(0)
        log.info("First player is:  " + player.getName())
        speechText += "\n"
        speechText += player.getName() + ", "

        getHeroQuestion(session, speechText);

    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse setPlayerName(Slot query, final Session session) {
        String playerName = query.getValue()

        def speechText = ""

        if (!"last player".equalsIgnoreCase(playerName)) {
            User user = new User()
            user.setName(playerName)
            ArrayList<User> playerList = (ArrayList) session.getAttribute("playerList")
            playerList.add(user)
            session.setAttribute("playerList", playerList)
            speechText = "OK.  Tell me the next player’s name or say Last Player to move on."
        } else {
            speechText = "How many questions should I ask each player?"
        }

        askResponse(speechText, speechText)

    }

    private SpeechletResponse askResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Hero Quiz");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    private SpeechletResponse askResponseFancy(String cardText, String speechText, String fileUrl) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Hero Quiz");
        card.setContent(cardText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        log.info("making welcome audio")
        SsmlOutputSpeech fancySpeech = new SsmlOutputSpeech()
        fancySpeech.ssml = "<speak><audio src=\"${fileUrl}\"/> ${speechText}</speak>"
        log.info("finished welcome audio")
        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(fancySpeech);

        SpeechletResponse.newAskResponse(fancySpeech, reprompt, card);
    }

    /**
     * Creates a {@code SpeechletResponse} for the hello intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHeroAnswer(Slot query, final Session session) {

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Hero Quiz");
        def speechText = ""

        String searchTerm = query.getValue()
        log.info("Guessed answer is:  " + query.getName())
        log.info("Guessed answer is:  " + query.getValue())

        Question question = (Question) session.getAttribute("lastQuestionAsked")
        def answer = question.getText()
        decrementQuestionCounter(session)
        String nextPrompt = (getQuestionCounter(session) != 0) ? "Say next question when you're ready to continue." : ""

        if(searchTerm.toLowerCase() == answer.toLowerCase()) {
            speechText = "You got it right.  " + nextPrompt
            incrementScore(session)
            if(getQuestionCounter(session) == 0) {
                int score = getScore(session)
                int numberOfQuestions = getNumberOfQuestions(session);
                speechText += "You got ${score} out of ${numberOfQuestions} questions correct."
            }
        } else {
            speechText = "You got it wrong.  You said " + query.getValue() + "But I was looking for " + answer + ".  " + nextPrompt
            log.info("I heard this answer:  " + searchTerm.toLowerCase())
            log.info("I expected this answer:  " + answer)
            if(getQuestionCounter(session) == 0) {
                int score = getScore(session)
                int numberOfQuestions = getNumberOfQuestions(session);
                speechText += "You got ${score} out of ${numberOfQuestions} questions correct."
            }
        }

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);
        if(getQuestionCounter(session) != 0) {
            return askResponse(speechText, speechText)
        } else {
            return SpeechletResponse.newTellResponse(speech, card);
        }
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse() {
        String speechText = "Say quiz me to test your superhero knowledge.";

        askResponse(speechText, speechText)
    }

    private void incrementScore(Session session) {
        int score = (int) session.getAttribute("score")
        score++
        session.setAttribute("score", score)

    }

    private int getScore(Session session) {
        (int) session.getAttribute("score")
    }

    private void decrementQuestionCounter(Session session) {
        int questionCounter = (int) session.getAttribute("questionCounter")
        questionCounter--
        session.setAttribute("questionCounter", questionCounter)

    }

    private int getQuestionCounter(Session session) {
        (int) session.getAttribute("questionCounter")
    }

    private int getNumberOfQuestions(Session session) {
        (int) session.getAttribute("numberOfQuestions")
    }


    /**
     * Initializes the instance components if needed.
     */
    private void initializeComponents(Session session) {
        if (amazonDynamoDBClient == null) {
            amazonDynamoDBClient = new AmazonDynamoDBClient();
            ScanRequest req = new ScanRequest();
            req.setTableName("HeroQuiz");
            ScanResult result = amazonDynamoDBClient.scan(req)
            List quizItems = result.items
            tableRowCount = quizItems.size()
            log.info("This many rows in the table:  " + tableRowCount)
        }
        session.setAttribute("score", 0)
        LinkedHashMap<String, Question> askedQuestions = new LinkedHashMap()
        session.setAttribute("askedQuestions", askedQuestions)
    }
}
