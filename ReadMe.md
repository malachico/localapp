# Distributed Systems, Assignment 1.
### Names:
- malachi Cohen 203085295
- Amir Arbel

## Requirments:
- Did you think for more than 2 minutes about security? Do not send your credentials in plain text!
    - TODO
---
- Did you think about scalability? Will your program work properly when 1 million clients
connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system, be sure it is scalable!
    - Yes, the manager works in parallel. Each task work separately in different thread.
---
- What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system? Think of more possible issues that might arise from failures. What did you do to solve it? What about broken communications? Be sure to handle all fail-cases!
    - If a node dies and already took a task, it doesn't clear it from the tasks queue immediately.
 Only when he finish the task.
However, the message will be unvisiable for other workers within the first minuete since taken. So if it dies the message will return to the queue and another worker will take it.
---
- Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application!
    - We made a thread for each task in the manager, so tasks won't wait for each other. 
But, in the worker for example the analyze is done quickly and is a small task distributed between lot of workers so it can be done one-threaded.
---
- Did you run more than one client at the same time? Be sure they work properly, and finish properly, and your results are correct.
    - Yes, we have ran 10 apps simultaneously. All correct.
---
- Do you understand how the system works? Do a full run using pen and paper, draw the different parts and the communication that happens between them.
    - Yes. we understand it thoroughly.
---
- Did you manage the termination process? Be sure all is closed once requested!
    - TODO check it works
---
- Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
    - TODO: Dont understand
---
- Are all your workers working hard? Or some are slacking? Why?
    - TODO: Dont understand
---
- Lastly, are you sure you understand what distributed means? Is there anything in your system awaiting another?
    - Yes, A distributed system is a software system in which components located on networked computers communicate and coordinate their actions by passing messages. The components interact with each other in order to achieve a common goal. In our case the goal is to parse tweets of local apps.
