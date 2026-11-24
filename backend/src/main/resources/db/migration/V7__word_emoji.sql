-- A picture (one emoji) per word. The concept holds the dictionary's emoji; a week word keeps its own copy,
-- frozen with the set, which the parent may change while the week is a draft.
alter table concept add column emoji varchar(32);
alter table week_word add column emoji varchar(32);

-- Emoji for the seed dictionary, only where one picture clearly means the word.
-- Matched by topic and primary English form ("orange" is both a colour and a fruit).
create temporary table seed_emoji (
    topic varchar(40) not null,
    en    varchar(60) not null,
    emoji varchar(32) not null
);

insert into seed_emoji (topic, en, emoji) values
('animals', 'dog', '🐕'), ('animals', 'cat', '🐈'), ('animals', 'horse', '🐎'), ('animals', 'cow', '🐄'),
('animals', 'pig', '🐖'), ('animals', 'sheep', '🐑'), ('animals', 'goat', '🐐'), ('animals', 'hen', '🐔'),
('animals', 'duck', '🦆'), ('animals', 'mouse', '🐁'), ('animals', 'rabbit', '🐇'), ('animals', 'bird', '🐦'),
('animals', 'fish', '🐟'), ('animals', 'frog', '🐸'), ('animals', 'bear', '🐻'), ('animals', 'wolf', '🐺'),
('animals', 'fox', '🦊'), ('animals', 'lion', '🦁'), ('animals', 'tiger', '🐅'), ('animals', 'elephant', '🐘'),
('animals', 'monkey', '🐒'), ('animals', 'snake', '🐍'), ('animals', 'owl', '🦉'), ('animals', 'bee', '🐝'),
('animals', 'butterfly', '🦋'), ('animals', 'giraffe', '🦒'), ('animals', 'zebra', '🦓'),
('animals', 'squirrel', '🐿️'), ('animals', 'hedgehog', '🦔'), ('animals', 'turtle', '🐢'),
('body', 'eye', '👁️'), ('body', 'ear', '👂'), ('body', 'nose', '👃'), ('body', 'mouth', '👄'),
('body', 'hand', '✋'), ('body', 'leg', '🦵'), ('body', 'finger', '👆'), ('body', 'tooth', '🦷'),
('body', 'face', '🙂'), ('body', 'heart', '❤️'),
('home', 'house', '🏠'), ('home', 'door', '🚪'), ('home', 'window', '🪟'), ('home', 'bed', '🛏️'),
('home', 'sofa', '🛋️'), ('home', 'clock', '🕰️'), ('home', 'cup', '☕'), ('home', 'plate', '🍽️'),
('home', 'spoon', '🥄'), ('home', 'key', '🔑'), ('home', 'mirror', '🪞'), ('home', 'bath', '🛁'),
('home', 'computer', '💻'), ('home', 'phone', '📱'),
('describing', 'cold', '🥶'), ('describing', 'happy', '😀'), ('describing', 'sad', '😢'),
('describing', 'good', '👍'), ('describing', 'bad', '👎'), ('describing', 'slow', '🐌'),
('describing', 'funny', '😂'), ('describing', 'strong', '💪'),
('colours', 'red', '🟥'), ('colours', 'blue', '🟦'), ('colours', 'green', '🟩'), ('colours', 'yellow', '🟨'),
('colours', 'black', '⬛'), ('colours', 'white', '⬜'), ('colours', 'orange', '🟧'), ('colours', 'brown', '🟫'),
('colours', 'purple', '🟪'),
('actions', 'run', '🏃'), ('actions', 'swim', '🏊'), ('actions', 'read', '📖'), ('actions', 'write', '✍️'),
('actions', 'sing', '🎤'), ('actions', 'dance', '💃'), ('actions', 'drink', '🥤'), ('actions', 'sleep', '😴'),
('actions', 'walk', '🚶'), ('actions', 'draw', '🎨'), ('actions', 'look', '👀'), ('actions', 'speak', '🗣️'),
('actions', 'cry', '😭'), ('actions', 'laugh', '😆'),
('nature', 'sun', '☀️'), ('nature', 'rain', '🌧️'), ('nature', 'snow', '❄️'), ('nature', 'wind', '💨'),
('nature', 'cloud', '☁️'), ('nature', 'tree', '🌳'), ('nature', 'flower', '🌸'), ('nature', 'leaf', '🍃'),
('nature', 'sea', '🌊'), ('nature', 'mountain', '⛰️'), ('nature', 'forest', '🌲'), ('nature', 'moon', '🌙'),
('nature', 'star', '⭐'), ('nature', 'rainbow', '🌈'), ('nature', 'stone', '🪨'), ('nature', 'island', '🏝️'),
('nature', 'ice', '🧊'), ('nature', 'fire', '🔥'),
('school', 'school', '🏫'), ('school', 'teacher', '🧑‍🏫'), ('school', 'book', '📕'), ('school', 'pen', '🖊️'),
('school', 'pencil', '✏️'), ('school', 'bag', '🎒'), ('school', 'chair', '🪑'), ('school', 'ruler', '📏'),
('school', 'paper', '📄'), ('school', 'picture', '🖼️'), ('school', 'map', '🗺️'), ('school', 'notebook', '📓'),
('school', 'scissors', '✂️'),
('family', 'grandmother', '👵'), ('family', 'grandfather', '👴'), ('family', 'baby', '👶'),
('family', 'family', '👪'), ('family', 'boy', '👦'), ('family', 'girl', '👧'), ('family', 'man', '👨'),
('family', 'woman', '👩'), ('family', 'child', '🧒'),
('numbers', 'one', '1️⃣'), ('numbers', 'two', '2️⃣'), ('numbers', 'three', '3️⃣'), ('numbers', 'four', '4️⃣'),
('numbers', 'five', '5️⃣'), ('numbers', 'six', '6️⃣'), ('numbers', 'seven', '7️⃣'), ('numbers', 'eight', '8️⃣'),
('numbers', 'nine', '9️⃣'), ('numbers', 'ten', '🔟'), ('numbers', 'hundred', '💯'),
('clothes', 'shirt', '👔'), ('clothes', 'T-shirt', '👕'), ('clothes', 'dress', '👗'),
('clothes', 'trousers', '👖'), ('clothes', 'shoes', '👟'), ('clothes', 'sock', '🧦'), ('clothes', 'hat', '🎩'),
('clothes', 'coat', '🧥'), ('clothes', 'scarf', '🧣'), ('clothes', 'glove', '🧤'), ('clothes', 'boots', '🥾'),
('toys', 'ball', '⚽'), ('toys', 'game', '🎲'), ('toys', 'kite', '🪁'), ('toys', 'puzzle', '🧩'),
('toys', 'balloon', '🎈'), ('toys', 'teddy bear', '🧸'), ('toys', 'drum', '🥁'),
('food', 'apple', '🍎'), ('food', 'banana', '🍌'), ('food', 'bread', '🍞'), ('food', 'milk', '🥛'),
('food', 'water', '💧'), ('food', 'egg', '🥚'), ('food', 'cheese', '🧀'), ('food', 'butter', '🧈'),
('food', 'cake', '🎂'), ('food', 'soup', '🍲'), ('food', 'meat', '🥩'), ('food', 'rice', '🍚'),
('food', 'salt', '🧂'), ('food', 'tea', '🍵'), ('food', 'juice', '🧃'), ('food', 'carrot', '🥕'),
('food', 'potato', '🥔'), ('food', 'tomato', '🍅'), ('food', 'cucumber', '🥒'), ('food', 'orange', '🍊'),
('food', 'pear', '🍐'), ('food', 'cherry', '🍒'), ('food', 'strawberry', '🍓'), ('food', 'lemon', '🍋'),
('food', 'grapes', '🍇'), ('food', 'ice cream', '🍦'), ('food', 'candy', '🍬'), ('food', 'cookie', '🍪'),
('food', 'pizza', '🍕'), ('food', 'porridge', '🥣'), ('food', 'honey', '🍯'), ('food', 'chocolate', '🍫'),
('food', 'pancake', '🥞'), ('food', 'nut', '🥜'), ('food', 'mushroom', '🍄'),
('transport', 'car', '🚗'), ('transport', 'bus', '🚌'), ('transport', 'train', '🚆'),
('transport', 'plane', '✈️'), ('transport', 'ship', '🚢'), ('transport', 'boat', '⛵'),
('transport', 'bicycle', '🚲'), ('transport', 'tram', '🚋'), ('transport', 'taxi', '🚕'),
('transport', 'truck', '🚚');

update concept c
set emoji = s.emoji
from word_form f, seed_emoji s
where f.concept_id = c.id and f.lang = 'en' and f.is_primary
  and c.origin = 'SEED' and c.topic = s.topic and f.text = s.en;

drop table seed_emoji;
