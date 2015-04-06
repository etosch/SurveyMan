package edu.umass.cs.surveyman.qc;

import edu.umass.cs.surveyman.analyses.AbstractSurveyResponse;
import edu.umass.cs.surveyman.analyses.KnownValidityStatus;
import edu.umass.cs.surveyman.survey.*;
import edu.umass.cs.surveyman.survey.exceptions.SurveyException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by etosch on 4/3/15.
 */
public class LexicographicRespondent extends AbstractRespondent {

    private AbstractSurveyResponse surveyResponse;

    static protected void sortByData(List<Component> componentList) {
        for (int i = 0 ; i < componentList.size()-1 ; i++) {
            for (int j = 1 ; j < componentList.size() ; j++) {
                Component datum1 = componentList.get(i);
                Component datum2 = componentList.get(j);
                String comp1 = (datum1 instanceof StringComponent) ? ((StringComponent) datum1).data : ((HTMLComponent) datum1).data;
                String comp2 = (datum2 instanceof StringComponent) ? ((StringComponent) datum2).data : ((HTMLComponent) datum2).data;
                if (comp1.compareTo(comp2) > 0) {
                    componentList.set(i, datum2);
                    componentList.set(j, datum1);
                }
            }
        }
    }

    public LexicographicRespondent(Survey survey) {
        Interpreter interpreter = new Interpreter(survey);
        try {
            do {
                Question q = interpreter.getNextQuestion();
                List<Component> possibleAnswers = new ArrayList<Component>(q.options.values());
                sortByData(possibleAnswers);
                Component c = possibleAnswers.get(0);
                List<Component> ans = new ArrayList<Component>();
                ans.add(c);
                interpreter.answer(q, ans);
            } while (!interpreter.terminated());
            this.surveyResponse = interpreter.getResponse();
            this.surveyResponse.setKnownValidityStatus(KnownValidityStatus.YES);
        } catch (SurveyException e) {
            e.printStackTrace();
        }
    }

    @Override
    public AbstractSurveyResponse getResponse() {
        return surveyResponse;
    }
}