This REST API can access in one call all you process variable, from a taskId or from a caseId.

-------------------------------- Difference : --------------------------------
 1/ Perimeter: using http://../API/bpm/activityVariable/[activity_id]/[variable_name] or http://../API/bpm/caseVariable/[caseId]/[variableName], you need to know where is the variable (process, activity)
 2/ theses REST API can't handle HashMap, List or Datatype, only basic type
 3/ one variable at a call
 4/ not way to control security
 5/ one call per BDM
 6/ if the BDM has a children "load when needed", the basic RestAPI does not include it, and you have to deal with Javascript / RestAPI to get the first level of child, and there are no simple way to get a grand child
 7/ no way to protect the header.CommentOfTheManagerEmployeeMustNotAccess in anyway
 8/ case is archive ? REST API to call are different ( ! ) . So, building an overview is 10 rest API ? You have to call 10 more REST API, different REST API, to get the result.

-------------------------------- first step --------------------------------
 When you use some processes variables (process, or local variable), use this REST Api to access all the variable in one call. All variables are delivered on a Json format (even HashMap and List, and Datatype) and you don't need to care if the variable is a process variable or a local variable.

So, you can use
 ../API/extension/context?caseId={{caseId}}
 or
 ../API/extension/context?taskId={{taskId}}

-------------------------------- How to pilot the result ? --------------------------------

To pilot which variable has to be retrieved and return, you can defined a local variable "context", or a process variable "globalcontext" which contains some JSON information.
 For example, set as localvariable "context" the following information:
 { "aBoolean":"data",
 "aDate":"data",
 "aHashMap":"data",
 "aInteger":"data",
 "aList":"data",
 "aLong":"data",
 "aString":"data",
 "aLocalVariable":"data",
 "aDataType":"datatype" }

then you get as a result :
 { "aBoolean":true,
 "aLocalVariable":"yes, Local variable too",
 "aHashMap":{"age":34,"lastname":"Bates","firstname":"Walter"},
 "aString":"Hello the word",
 "aInteger":54,
 "aList":["Hello","the","world"],
 "aDataType":{ "lines":
 [ {"product":"BPAD","lineNumber":"100"},
 {"product":"AI","lineNumber":"200"}
 ],
 "details":
 {"address":"44 Tahoha St","country":"USA","city":"San Francisco"},
 "headerNumber":0,
 "name":"TheDatatype Name"
 },
 "aLong":12,
 "aDate":"2016-04-27T22:39:32+0000"
 }
 -------------------------------- access the BDM--------------------------------
 When you have a BDM variable like "summerOrder", ask in the pilot :
 "summerOrder" : {
 "name": "data",
 "ticket": "",
 "lines" : { "linename" : "data",
 "ticket" : { "solicitante" : "data" },
 "price":"data"
 }
 }
 Saying that, you will get the variable, and the children "ticket" and all fields in tickets (if ticket is a MULTIPLE, then you get a LIST of RECORD). Then you ask to get lines, and in lines, subchild "linename" and "price".
 Then, if the BDM has more attributes (like in the lines an attribute "for_the_manager_only", then this information is not part of the result (except if you ask "")

-------------------------------- all variables --------------------------------
 Get it simple, get all ! And in fact, if you don't specify a pilot, that what you have.
 if you want all variables (process, activity, BDM) :
 { "*":"all" }
 Nota : if you ask with a parameters "taskId" you get all the LOCAL variable, the PROCESS variables and the BDM variables. With a parameter caseId, you get only the process variable and the BDM variables.

-------------------------------- case is archived ? --------------------------------
 If the case 9002 is now archived, just continue to ask the case 9002 : the REST API get all the information even if the case is archived. Enjoy a simple way to build the Overview page !

-------------------------------- log --------------------------------
 in the URL, ask "&log=true" and then you get additional result in the JSON : source of data, performance to fetch the result. And on the server too, you will have more information in the log

--------------------------------------- Security --------------------------------

All the REST API activityVariable/ and caseVariable are based on the permission access task_visualization. So, if your process contains a sensitive information like "managerComment", at any moment, the employe can access this information via one of this API.
 To avoid that:
 * change the permission access of this two REST API to "onlyadministratorway"
 * in each activity, define a local variable "context" where you define what the user can see.
 Doing that, the REST API will deliver only what it's define in the local variable. And because the variable is on the server, user has no way to change it.
