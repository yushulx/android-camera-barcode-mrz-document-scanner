package com.dynamsoft.mrzscannerbundle.ui;

/**
 * @author: dynamsoft
 * Time: 2024/12/11
 * Description:
 */
public class MRZData {
	private final String enumDocType;
	private final String firstName;
	private final String lastName;
	private final String sex;
	private final String issuingState;
	private final String nationality;
	private final String dateOfBirth;
	private final String dateOfExpire;
	private final String documentNumber;
	private final int age;
	private final String mrzText;

	public MRZData(String firstName, String lastName, String sex,
	               String issuingState, String nationality, String dateOfBirth, String dateOfExpire,
	               String documentNumber, int age, String mrzText, String enumDocType) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.sex = sex;
		this.issuingState = issuingState;
		this.nationality = nationality;
		this.dateOfBirth = dateOfBirth;
		this.dateOfExpire = dateOfExpire;
		this.documentNumber = documentNumber;
		this.age = age;
		this.mrzText = mrzText;
		this.enumDocType = enumDocType;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getSex() {
		return sex;
	}

	public String getIssuingState() {
		return issuingState;
	}

	public String getNationality() {
		return nationality;
	}

	public String getDateOfBirth() {
		return dateOfBirth;
	}

	public String getDateOfExpire() {
		return dateOfExpire;
	}

	public String getDocumentType() {
		return enumDocType;
	}

	public String getDocumentNumber() {
		return documentNumber;
	}

	public int getAge() {
		return age;
	}

	public String getMrzText() {
		return mrzText;
	}
}
