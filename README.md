# Distributed Systems, Assignment 1.
### Names:
- Malachi Cohen 
- Amir Arbel

## Requirements:
* Did you think for more than 2 minutes about security? Do not send your credentials in plain text!
    - Jars (with the credentials file) are encrypted and the password is sent from within the application itself,
     saved as a program constant.

---

* Did you think about scalability? Will your program work properly when 1 million clients
connected at the same time? How about 2 million? 1 billion? Scalability is very important aspect of the system,
 be sure it is scalable!
    - Yes, the manager works in parallel. Each task (tweet) will work in different worker (thread).
    The amount of workers increase with the amount of tweets.

---
* What about persistence? What if a node dies? Have you taken care of all possible outcomes in the system?
    Think of more possible issues that might arise from failures. What did you do to solve it?
    What about broken communications? Be sure to handle all fail-cases!
    - If a node dies and already took a task, it doesn't clear it from the tasks queue immediately.
    Only when he finishes the task, it is cleared. However, the message will be invisible for other workers
    within the first minute since taken.
     So if it dies the message will return to the queue later and another worker will take it.

---
- Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application!
    - We made a thread for each task in the manager, so tasks won't wait for each other. Workers on the other hand, are
    single-threaded due to the low amount of time required to analyze a tweet.

---
- Did you run more than one client at the same time? Be sure they work properly, and finish properly, and your results are correct.
    - Yes, we ran 10 local applications simultaneously to test the configuration.

---
- Do you understand how the system works?
    Do a full run using pen and paper, draw the different parts and the communication that happens between them.
    - Yes. we understand it thoroughly.

---
- Did you manage the termination process? Be sure all is closed once requested!
    - The manager receives a termination signal once an answer was received in the local application.
    It sends the termination to the workers, and when they are down, shuts itself down.
    Local application dies after creating the answer and statistics file.

---
- Did you take in mind the system limitations that we are using? Be sure to use it to its fullest!
    - We used all of the necessary system components from the Amazon cloud.

---
- Are all your workers working hard? Or some are slacking? Why?
    - All of the workers receive the same workload since they are getting it from the SQS.

---
- Lastly, are you sure you understand what distributed means? Is there anything in your system awaiting another?
    - Yes, A distributed system is a software system in which components located on networked computers communicate
     and coordinate their actions by passing messages. The components interact with each other in order to achieve a
      common goal. In our case the goal is to parse tweets of local apps.
