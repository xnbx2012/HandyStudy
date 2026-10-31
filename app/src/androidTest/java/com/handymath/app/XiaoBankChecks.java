package com.handymath.app;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.util.*;

final class XiaoBankChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static void run(Context c)throws Exception{
    File zip=new File(c.getExternalFilesDir(null),"xiao1000-2027.zip");
    List<BankRepository> banks=BankRepository.importZips(c,Uri.fromFile(zip));
    check(banks.size()==5,"one ZIP imports all five parts");
    Map<String,Integer> expected=new HashMap<>();
    expected.put("mayuan",396);expected.put("maozhongte",115);expected.put("xinsixiang",220);expected.put("shigang",251);expected.put("sixiu",166);
    Set<String> ids=new HashSet<>();int total=0,single=0,multi=0,chapters=0;
    for(BankRepository bank:banks){
      String slug=bank.id.substring("politics-xiao1000-2027-".length());
      check(expected.get(slug)==bank.questions.size(),"part count preserved: "+slug);
      Set<String> chapterNames=new HashSet<>();
      for(BankRepository.Question q:bank.questions){
        check(ids.add(q.id),"unique source question identity");total++;chapterNames.add(q.chapter);
        check(!q.questionMarkdown.contains("≯#")&&!q.answerMarkdown.contains("≯#"),"no encrypted text remaining");
        check(q.referenceAvailable&&!q.answerMarkdown.isEmpty(),"original reference answer available");
        check(q.knowledgeIds.length()>0&&q.tags!=null,"chapter and knowledge metadata load");
        for(int n=0;n<q.knowledgeIds.length();n++)check(bank.knowledge.under(q.knowledgeIds.getString(n),"xiao-"+q.id.split("_")[2]),"knowledge hierarchy reaches part root");
        if(q.type.equals("choice")){single++;check(q.expectedChoice.matches("[ABCD]"),"single choice answer");}
        else {multi++;check(q.type.equals("multi-choice")&&q.expectedChoice.matches("[ABCD]{2,4}"),"multi choice answer");}
        if(slug.equals("sixiu"))check(!q.chapter.contains("中国特色社会主义进入新时代"),"misplaced thought chapters corrected");
      }
      chapters+=chapterNames.size();
      BankRepository reopened=BankRepository.loadLocation(c,bank.location());
      check(reopened.questions.size()==bank.questions.size(),"installed bank can reopen");
    }
    check(total==1148&&single==528&&multi==620&&chapters==52,"complete coverage and chapter counts");
  }
}
