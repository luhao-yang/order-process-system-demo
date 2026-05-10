
# Scenario - Order Process &  Notification System
You are building a simplified order-processing and notification system using Java. The system should: 
•	Create 4 API endpoints including create, retrieve, update, and search order. 
•	Accept and create a new order request. 
•	Update order status.
•	Search orders.
•	Send out notification to external system when order has been created or status changed. 
Client of the system can be a Web page, or mocked API calls. 


# Coding Requirements - Core Java Concepts, Java SpringBoot or Java Spring MVC
1.	Order API Management
Expose REST endpoints to support the following functionalities:
•	Create a new order
•	Retrieve order details
•	Update order status
•	Search orders (pagination or filtering)
Orders must follow the controlled lifecycle including following statuses:
•	Initial state: CREATED
•	Other states: CANCELLED, COMPLETED

2.	Notifications
All order status change will be sent to external systems.
•	Notification implementation need to support multiple channels, such as email, SMS, etc
•	A configuration file need to be used to configure notification channels
•	WireMock can be used
•	Eventing can be used

3.	Persistence
Orders must be persisted using a database (in-memory database can be used)
4.	Error Handling
All errors must return consistent, meaningful HTTP responses, and errors must be logged in a meaningful way. 
Explain how retry is implemented. 
5.	Security
Utilise Spring Security Secure the API. Mock or simplified security implementations are acceptable.
6.	Testing 
Provide automated tests.
7.	Documentation
A README.md required to explain why key technical decisions were made in the code. And proper comments added to the code. 
