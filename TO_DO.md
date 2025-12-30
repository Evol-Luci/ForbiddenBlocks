# Tasks or Errors to be taken care of

- The minecraft fabric mod in this project is meant to allow players to hold an item in their hand and press a hotkey to Forbid it being placed in the world. This forbiddance should be persisted across starting and stopping the game until the point it is removed by the user. 
  
  - ~~Currently we use Cloth-Config as a depend. It unfortunately is slow to update so I would like to use a newer one. The creator of cloth recommended the owo-lib which another mod I use already needs. Is there any way we could replace the cloth config depend to owo?~~
  
  - **DONE:** Successfully migrated from Cloth-Config to owo-lib.

  - If we can switch to owo, please design an implementation that will allow us to implement owo and remove clothconfig from the project. 
  
  - If owo will not work, can we use another library? 
