class AppStrings {
  // Private constructor to prevent creating instances of this class
  AppStrings._();

  static const String appName = 'Triplor';

  //Create Adventure Screen Strings
  static const String createAdventureHeading = 'Create Adventure';
  static const String cancelCreateAdventure = 'Cancel';
  static const String whereTo = 'Where to?';
  static const String tripStyle = 'Trip Style';
  static const String locationFieldHint = 'City, Country, or Region';
  static const String startDate = 'START';
  static const String endDate = 'END';
  static const String selectDate = 'Select date';
  static const String when = 'When?';
  static const String lookingFor = 'Looking for...';
  static const String maxPeople = 'Max People';
  static const String thePlan = 'The Plan';
  static const String travelBuddy = 'Travel Buddy';
  static const String travelBuddySubtitle = 'Just one person';
  static const String groupTrip = 'Group Trip';
  static const String groupTripSubtitle = '2+ people';
  static const String descriptionHint =
      'Share a bit about your itinerary or what kind of travel buddy you are looking for...';
  static const String safetyMessage =
      'Your trip details are visible only to verified users to ensure safety and trust within the community.';
  static const String saveAdventure = 'Save Adventure';
  static const String adventureCreatedSuccess =
      'Adventure created successfully!';
  static const String errorPrefix = 'Error: ';
  static const String dateFormat = 'MMM d';
  static const String createAdventureValidationError =
      'Please fill all required fields';

  //Routes
  static const String homeRoute = '/home';
}
