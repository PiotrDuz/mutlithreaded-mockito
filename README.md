An example showing how to make Mockito compatible with Integration tests running async processes. 
Article: https://piotrd.hashnode.dev/mockito-and-strange-unfinishedstubbing-problems

Usage: 
- remember about mockito-extensions folder. This is how you register an extensions to mockito
- Classes MockitoSynchronizer and SynchronizedMockMaker are ready to be used. Mind the static map holding the locks. Weak keys are to not hold onto the objects for too long, Spring tests can create several ApplicationContextees.
