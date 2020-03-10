
1/ Hoiw to get a document from a content storage id ? 

     final SearchOptionsBuilder builder = new SearchOptionsBuilder(0, 2);
      builder.filter(DocumentsSearchDescriptor.CONTENT_STORAGE_ID , contentStorageId );

                final SearchResult<Document> searchResult = processAPI.searchDocuments( builder.done());
   
   and I got
   java.lang.IllegalArgumentException: the field 'contentStorageId' is unknown for the entity searched using SearchDocumentDescriptor
   
   Looking the documentation : 
   			SearchResult<Document>	searchDocuments(SearchOptions searchOptions)
			Search for documents that match the search options.
So I feel that I can user the DocumentsSearchDescriptor isn't ? 
 
 
 
 
 
 3/ 
 http://localhost:8080/bonita/portal/documentDownload?contentStorageId=609
 http://localhost:8080/bonita/portal/resource/processInstance/Aegrotat/1.31/API/documentDownload?fileName=aDoc%202.pdf&contentStorageId=609
 
 
 ==> Walter Bate : Yes, it is the initiator
 ==> Jan Fisher : Yes, part of an actor
 ==> Williams Jobs : no
 
 ---------------------------------------- test document 2
 {
     "firstName":"data",
     "lastName":"data",
     "publicDocument":"data",
     "medicalDocument":"initiator;task:Medical",
     "teacherDocument":"initiator;actor:TeacherActor",
     "privateTeacherDocument" : "task:Teacher",
     "privateActorTeacherDocument" : "actor:TeacherActor
}

-----------------------------------------------------
{ "application": { 
	"idNumber":"data", 
	"preferredFirstName":"data", 
	"preferredLastName":"data", 
	"assessmentType":"data", 
	"totalFee":"data", 
	"uoaEmailAddress":"data", 
	"assessments": { 
		"courseTitle" : "data", 
		"assessmentDate" : "format:date", 
		"assessmentType" : "data", 
		"academicHeadComment": "actor:AcademicHeadSupervisor;actor:ExaminerSupervisor,actor:Academic Head;actor:Communicate Exam Office"} },  
"listDocsStudent": "initiator;actor:UHSC Receptionist;actor:Medical Moderator",  
"listDocsExaminer": "profile:examiner;actor:Communicate Exam Office;actor:Examiner;actor:Academic Head", 
"iamURL":"data" }


 William.jobs : student					OK
 helen Kelly : Moderator		==> [UHSC Receptionist] / [Compassionate Moderator] / [Medical Moderator]
 Jan Fisher : teacher
 
 http://localhost:8080/bonita/portal/resource/taskInstance/Aegrotat/1.32/Check%20Completed/API/extension/context?caseId=38002
 
 
 privateActorTeacherDocument (	101) ==> William Jobs
 			http://localhost:8080/bonita/portal/documentDownload?contentStorageId=101&fileName=aDoc%202.pdf
 			
 medicalDocument(	104) ==> Walter.Bates
  			http://localhost:8080/bonita/portal/documentDownload?contentStorageId=104&fileName=aDoc%202.pdf
 
 
http://localhost:8080/bonita/API/extension/context?caseId=1001