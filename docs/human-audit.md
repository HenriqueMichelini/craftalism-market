at: TRADE GUI
- feat: chat message when player add or remove 1 item quantity. Should be like: "Quantity: <quantity>". Green for buy, Gold for sell. 
- feat: More buttons for INCREMENT/DECREMENT. expected layout:
  - INCREMENT 1, SLOT 7, ItemQuantity 1. LIGHT_GREEN_GLASS_PANEL.
  - INCREMENT 8, SLOT 8, ItemQuantity 8. LIGHT_GREEN_GLASS_PANEL.
  - INCREMENT 32, SLOT 17, ItemQuantity 32. LIGHT_GREEN_GLASS_PANEL.
  - INCREMENT 64, SLOT 18, ItemQuantity 64. LIGHT_GREEN_GLASS_PANEL.
  - INCREMENT 576, SLOT 27, ItemQuantity 1. DARK_GREEN_GLASS_PANEL.
  - INCREMENT 2304, SLOT 28, ItemQuantity 1. ENCHANTED DARK_GREEN_GLASS_PANEL.
  - DECREMENT 1, SLOT 0, ItemQuantity 1. LIGHT_RED_GLASS_PANEL.
  - DECREMENT 8, SLOT 1, ItemQuantity 8. LIGHT_RED_GLASS_PANEL.
  - DECREMENT 32, SLOT 10, ItemQuantity 32. LIGHT_RED_GLASS_PANEL.
  - DECREMENT 64, SLOT 11, ItemQuantity 64. LIGHT_RED_GLASS_PANEL.
  - DECREMENT 576, SLOT 20, ItemQuantity 1. DARK_RED_GLASS_PANEL.
  - DECREMENT 2304, SLOT 21, ItemQuantity 1. ENCHANTED DARK_RED_GLASS_PANEL.
- fix: remove reduntand item quantity. item display already has the quantity value in its description.

at: buy/sell operation logic.
- fix: craftalism-economy has a 4 decimals rule. so, 1$ can be read as 10000. The market plugin doesn't have this feature.
