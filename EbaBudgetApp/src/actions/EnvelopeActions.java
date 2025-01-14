package actions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import data.Database;
//import dataAccess.EnvelopeAccess;
//import dataObjects.Balance;
import dataObjects.Envelope;
import settings.EnvelopeSettings;
import tickets.ResponseTicket;

public class EnvelopeActions extends precisionOperations{

	//sets the priority of envelope, changes the rest accordingly
	public static void setPriority(ResponseTicket response, Envelope envelope, int priority) {

		if(response == null) {
			throw new IllegalArgumentException("Response is null");
		}
		if(envelope == null) {
			response.addErrorMessage("Envelope cannot be null");
			return;
		}

		if(!Database.hasEnvelope(envelope.getName())) {
			response.addErrorMessage("Envelope is not in Envelope Access");
			return;
		}

		List<Envelope> envelopes = Database.getEnvelopes();

		if(priority <= 0 || priority > envelopes.size()) {
			response.addErrorMessage("Invalid envelope edit, prority outside of range");
			return;
		}
		int prevPriority = envelope.getPriority();

		if(priority == prevPriority) {
			response.addInfoMessage("No change to envelope priority");
			return;
		}


		for(int index = 0; index < envelopes.size(); index++) {
			//current envelope
			Envelope temp = envelopes.get(index);
			//if current envelope is envelope given
			if(temp.getName().equals(envelope.getName())) {
				temp.setPriority(priority);
				continue;
			}
			//if current envelope should be moved up by priority change
			if(temp.getPriority() >= priority && temp.getPriority() < prevPriority) {
				temp.setPriority( temp.getPriority() + 1);
			}
			//if current envelope should be moved down by priority change
			else if(temp.getPriority() <= priority && temp.getPriority() > prevPriority) {
				temp.setPriority( temp.getPriority() - 1);

			}
		}

		response.addInfoMessage("Envelope " + envelope.getName() + " priority changed to " + priority);


		return;
	}

	//changes the name of the envelope
	public static void EditName(ResponseTicket response, Envelope e, String name) {

		if(response == null) {
			throw new IllegalArgumentException("Response is null");
		}
		if(e == null) {
			response.addErrorMessage("Envelope cannot be null");
			return;
		}
		if(name == null) {
			response.addErrorMessage("Envelope name cannot be changed to null");
			return;
		}
		if(e.getName().equals(name)) {
			response.addInfoMessage("No change to envelope name");
			return;
		}

		List<Envelope> envelopes = Database.getEnvelopes();


		for(int index = 0; index < envelopes.size(); index++) {
			if(e.equals(envelopes.get(index))) continue;
			if(name.equals(envelopes.get(index).getName())) {
				response.addErrorMessage("Invalid envelope edit, name is not unique");
				return;
			}
		}

		String prevName = e.getName();

		e.setName(name);

		response.addInfoMessage("Envelope " + prevName + " is now " + name);

	}

	/* NOTE: CAP SETTING IS NOT CHECKED IN TRANSFER IT IS ONLY CHECKED FOR DEPOSITS INTO BANK ACCOUNT I.E DEPOSIT BANK STATEMENT*/
	//Transfers amount from e1 to e2
	public static void Transfer(ResponseTicket response, Envelope e1, Envelope e2, BigDecimal amount) {

		if(amount.doubleValue() <= 0) {
			response.addErrorMessage("Cannot withdraw an amount less than or equal to 0");
			return;
		}
		
		
		if(e1 == null && e2 == null) {
			response.addErrorMessage("Both envelopes cannot be null");
			return;
		}
		//if e1 is null, it is a deposit
		if(e1 == null) {
			depositIntoEnvelope(response, e2, amount);
			return;
		}
		//if e2 is null, it is a withdrawal
		if(e2 == null) {
			withdrawFromSingleEnvelope(response, e1, amount);
			return;
		}

		if(e1.equals(e2)) {
			response.addErrorMessage("Cannot transfer to the same envelope");
			return;
		}

		if(amount.doubleValue() <= 0) {
			response.addErrorMessage("Invalid envelope transfer, amount cannot be less than or equal to 0");
			return;
		}
		if(e1.getAmount().doubleValue() < amount.doubleValue()) {
			response.addErrorMessage("Invalid envelope transfer, amount cannot be more than envelope amount");
			return;
		}
		
		BigDecimal fromAmount = subtract(e1.getAmount(), amount);
		BigDecimal toAmount = add(e2.getAmount(), amount);
		
		e1.setAmount(fromAmount);
		e2.setAmount(toAmount);

		
		response.addInfoMessage("$" + amount + " has been transferred from " + e1.getName() + " to " + e2.getName());

	}
	
	public static void depositIntoAll(ResponseTicket response, BigDecimal amount) {
				
		
		BigDecimal fullAmount = amount;
		List<Envelope> envelopes = Database.getEnvelopes();
		//deposit into those with percent fill setting
		for(int index = 1; index <= envelopes.size(); index++) {
			
			Envelope envelope = Database.getEnvelopeByPriority(index);
			
			if(envelope.getFillSetting() == EnvelopeSettings.percentage) {
				
				// EX: 10 / 100 = 0.1
				BigDecimal percentAmount = divide(envelope.getFillAmount(), 100);
				
				// EX: 500 * 0.1 = 50
				BigDecimal tempAmount = multiply(fullAmount, percentAmount);
				
				//format temp amount
				int formattedAmount = multiply(tempAmount, new BigDecimal(100)).intValue();
				tempAmount = divide(formattedAmount, 100);
				
				//deposit tempAmount
				deposit(response, envelope, tempAmount);
				
				//subtract tempAmount from amount
				amount = subtract(amount, tempAmount);

				response.addInfoMessage("deposited " + tempAmount + " into " + envelope.getName());
			}

			if(amount.doubleValue() == 0) return;
		}
		
		
		
		//deposit rest -- index == priority
		for(int index = 1; index <= envelopes.size(); index++) {
			
			Envelope e = Database.getEnvelopeByPriority(index);
			
			BigDecimal amountToDeposit;
			
			switch(e.getFillSetting()) {
				default: throw new IllegalStateException("envelope has an invalid fill setting");
				case EnvelopeSettings.percentage: continue;
				
				case EnvelopeSettings.fill: {
					
					//envelope has a cap
					if(e.hasCap()) {

						//get amounts needed
						int capAmount = e.getCapAmount();
						BigDecimal envelopeAmount = e.getAmount();

						//skip if envelope is full
						if(envelopeAmount.doubleValue() >= capAmount) continue;

						//get max amount envelope can be deposited
						amountToDeposit = subtract(new BigDecimal(capAmount), envelopeAmount);

						//check if deposit amount is less than max amount
						if(amountToDeposit.doubleValue() > amount.doubleValue()) amountToDeposit = amount;

					}
					//envelope does not have a cap
					else {
						amountToDeposit = amount;
					}
					
					
				} break;
				
				case EnvelopeSettings.amount: {
					
					amountToDeposit = new BigDecimal(e.getFillAmount());
					//if envelope fill amount is greater than amount available, change amountToDeposit to amount available
					if(amountToDeposit.doubleValue() > amount.doubleValue()) amountToDeposit = amount;
					
					//checks cap amount
					if(e.hasCap()) {
						
						BigDecimal amountTillFull = subtract(new BigDecimal(e.getCapAmount()), e.getAmount());
						//if envelope is full continue
						if(amountTillFull.doubleValue() <= 0) continue;
						//if amount to deposit is bigger than amount for envelope to be full, amount to deposit is the amount till full
						if(amountToDeposit.doubleValue() > amountTillFull.doubleValue()) {
							amountToDeposit = amountTillFull;
						}
					}
					
				} break;
			}
			
			
			deposit(response, e, amountToDeposit);
			amount = subtract(amount, amountToDeposit);
			//amount -= amountToDeposit;
			response.addInfoMessage("deposited $" + amountToDeposit + " into " + e.getName());
			
			if(amount.doubleValue() == 0) return;	
		}
		
		
		//deposit left over amount into envelope marked extra if there is one, otherwise validate will put it into the 1st priority envelope
		for(int index = 1; index <= envelopes.size(); index++) {
			Envelope e = Database.getEnvelopeByPriority(index);
			if(!e.isExtra()) continue;
			deposit(response, e, amount);
			amount = BigDecimal.ZERO;
			response.addInfoMessage("deposited $" + amount + " into " + envelopes.get(index).getName());

		}
		if(amount.doubleValue() > 0) {
			Envelope e = Database.getEnvelopeByPriority(1);
			if(e != null) {
				deposit(response, e, amount);
				response.addInfoMessage("deposited $" + amount + "into " + e.getName() + ". no envelope is marked as extra");
			}
			else {
				response.addErrorMessage("$" + amount + "is unnaccounted for. No envelopes exist to be deposited into");
			}
		}
		
	}
	
	private static void deposit(ResponseTicket response, Envelope e, BigDecimal amount) {
		e.setAmount(add(e.getAmount(), amount));
		//e.setAmount(e.getAmount() + amount);
		response.addInfoMessage("Envelope " + e.getName() + " has been deposited $" + amount);
		
	}
	
	public static void depositIntoEnvelope(ResponseTicket response, Envelope envelope, BigDecimal amount) {
		if(envelope == null) {
			depositIntoAll(response, amount);
			return;
		}
		if(amount.doubleValue() == 0) {
			response.addInfoMessage("Cannot deposit $0 from " + envelope.getName());
			return;
		}
		
		
		//if envelope does not have enough to cover amount, withdraw what's possible
		if(amount.doubleValue() < 0) {
			response.addErrorMessage("Cannot deposit negative amount");
			return;
		}
		//withdraw all of the amount
		
		deposit(response, envelope, amount);
		return;


		
	}

	private static void withdrawal(ResponseTicket response, Envelope e, BigDecimal amount) {
		e.setAmount(subtract(e.getAmount(), amount));
		//e.setAmount(e.getAmount() - amount);
		response.addInfoMessage("$" + amount + " has been withdrawn from Envelope " + e.getName());

	}

	public static void withdrawFromSingleEnvelope(ResponseTicket response, Envelope envelope, BigDecimal amount) {
		
		if(envelope == null) {
			response.addInfoMessage("Cannot withdraw from envelope, envelope does not exist");
			return;
		}
		if(amount.doubleValue() == 0) {
			response.addInfoMessage("Cannot withdraw $0 from " + envelope.getName());
			return;
		}
		
		//pointless to continue if envelope is empty
		if(envelope.getAmount().doubleValue() == 0) {
			response.addInfoMessage(envelope.getName() + " is already empty, cannot be withdrawn from");
			return;
		}
		
		
		
		//if envelope does not have enough to cover amount, withdraw what's possible
		if(envelope.getAmount().doubleValue() < amount.doubleValue()) {
			response.addErrorMessage("Invalid Envelope Transfer, Insufficient funds");
		}
		//withdraw all of the amount
		else {
			withdrawal(response, envelope, amount);
			return;
		}
		
		
		return;
	}
	
	public static BigDecimal withdrawFromEnvelope(ResponseTicket response, Envelope envelope, BigDecimal amount) {
		
		if(envelope == null) {
			response.addInfoMessage("Cannot withdraw from envelope, envelope does not exist");
			return amount;
		}
		if(amount.doubleValue() == 0) {
			response.addInfoMessage("Cannot withdraw $0 from " + envelope.getName());
			return BigDecimal.ZERO;
		}
		
		//pointless to continue if envelope is empty
		if(envelope.getAmount().doubleValue() == 0) {
			response.addInfoMessage(envelope.getName() + " is already empty, cannot be withdrawn from");
			return amount;
		}
		
		
		
		//if envelope does not have enough to cover amount, withdraw what's possible
		if(envelope.getAmount().doubleValue() < amount.doubleValue()) {
			amount = subtract(amount, envelope.getAmount());			
			withdrawal(response, envelope, envelope.getAmount());
		}
		//withdraw all of the amount
		else {
			withdrawal(response, envelope, amount);
			return BigDecimal.ZERO;
		}
		
		
		return amount;
	}


	
	public static void withdrawFromAll(ResponseTicket response, BigDecimal amount) {



		//loop from lowest to highest priority until looped through all envelopes or until amount is 0
		for(int index = Database.getEnvelopes().size(); index > 0 && amount.doubleValue() > 0; index--) {
			
			Envelope envelope = Database.getEnvelopeByPriority(index);
			
			amount = withdrawFromEnvelope(response, envelope, amount);
			
		}
		
		if(amount.doubleValue() == 0)
			response.addInfoMessage("Withdraw was successfull");
		else {
			response.addErrorMessage("Withdraw overdrafted account");
		}
		
	}

	

	//changes the fillSettings in envelope
	public static void EditSettings(ResponseTicket response, Envelope envelope, int fillSetting, int fillAmount) {

		if(response == null) {
			throw new IllegalArgumentException("Response is null");
		}

		if(envelope == null) {
			response.addErrorMessage("Envelope cannot be null");
			return;
		}

		if(envelope.getFillSetting() == fillSetting && envelope.getFillAmount() == fillAmount) {
			response.addInfoMessage("No change to Envelope fill settings");
			return;
		}

		if(fillAmount < 0 && fillSetting != EnvelopeSettings.fill) {
			response.addErrorMessage("Invalid envelope edit, fill amount cannot be less than 0");
			return;
		}

		//Checks and changes fill setting according to fillSetting requirements
		switch(fillSetting) {
			default:{
				response.addErrorMessage("Invalid envelope edit, Invalid fill setting");
				return;
			}
			case EnvelopeSettings.amount:{
	
				envelope.setFillSetting(fillSetting);
				envelope.setFillAmount(fillAmount);
				response.addInfoMessage("Fill setting has been changed to amount for " + envelope.getName());
				response.addInfoMessage("Fill amount has been set to " + fillAmount + " for " + envelope.getName());
	
	
			} break;
			case EnvelopeSettings.fill: {
				envelope.setFillSetting(fillSetting);
				envelope.setFillAmount(0);
				response.addInfoMessage("Fill setting has been changed to fill for " + envelope.getName());
	
			} break;
			case EnvelopeSettings.percentage: {
	
				int totalPercentage = getTotalFillPercentage(envelope);
	
				if(fillAmount > (100 - totalPercentage)) {
					response.addErrorMessage("Invalid envelope edit, total fill percentage cannot be greater than 100%");
					return;
				}
				envelope.setFillSetting(fillSetting);
				envelope.setFillAmount(fillAmount);
				response.addInfoMessage("Fill setting has been changed to percentage for " + envelope.getName());
				response.addInfoMessage("Fill percentage has been set to " + fillAmount + " for " + envelope.getName());
			}break;
		}

	}

	
	//changes the cap settings in envelope
	public static void EditCap(ResponseTicket response, Envelope envelope, boolean cap, int capAmount) {
		
		if(response == null) {
			throw new IllegalArgumentException("Response is null");
		}
		if(envelope == null) {
			response.addErrorMessage("Envelope cannot be null");
			return;
		}
		
		//no change happens
		if(envelope.hasCap() == cap && envelope.getCapAmount() == capAmount) {
			response.addInfoMessage("No change to envelope cap settings");
			return;
		}
		
		//invalid cap amount
		if(capAmount <= 0 && cap) {
			response.addErrorMessage("Invalid envelope edit, cap amount cannot be less than or equal to 0");
			return;
		}
		
		envelope.setCap(cap);
		
		if(cap) {
			envelope.setCapAmount(capAmount);
		}
		else {
			envelope.setCapAmount(0);
		}
		
		response.addInfoMessage("Envelope " + envelope.getName() + " now " + (cap?"has":"does not have") + " a cap");
		if(cap) {
			response.addInfoMessage("Envelope " + envelope.getName() + " now has a cap of " + capAmount);
		}
		
	}
	public static void EditExtra(ResponseTicket response, Envelope e, boolean extra) {
		if(response == null) {
			throw new IllegalArgumentException("Response is null");
		}
		if(e == null) {
			response.addErrorMessage("Envelope cannot be null");
			return;
		}
		if(e.isExtra() == extra) {
			response.addInfoMessage(e.getName() + " envelope's extra did not change");
			return;
		}
		//if extra given is not true
		if(!extra) {
			e.setExtra(extra);
		}
		//if extra given is true
		else {
			//setting envelope given to extra and rest to false
			List<Envelope> envelopes = Database.getEnvelopes();
			for(int index = 0; index < envelopes.size(); index++) {
				if(envelopes.get(index).equals(e)) {
					envelopes.get(index).setExtra(extra);
				}
				else {
					envelopes.get(index).setExtra(false);
				}
			}
		}
		
	}
	public static void EditDefault(ResponseTicket response, Envelope e, boolean Default) {
		if(response == null) {
			throw new IllegalArgumentException("Response is null");
		}
		if(e == null) {
			response.addErrorMessage("Envelope cannot be null");
			return;
		}
		if(e.isDefault() == Default) {
			response.addInfoMessage(e.getName() + " envelope's Default did not change");
			return;
		}
		//if default given is not true
		if(!Default) {
			e.setDefault(Default);
		}
		//if default given is true
		else {
			//setting envelope given to extra and rest to false
			List<Envelope> envelopes = Database.getEnvelopes();
			for(int index = 0; index < envelopes.size(); index++) {
				if(envelopes.get(index).equals(e)) {
					envelopes.get(index).setDefault(Default);
				}
				else {
					envelopes.get(index).setDefault(false);
				}
			}
		}
	}
	
	
	//does not count envelope given
	public static int getTotalFillPercentage(Envelope e) {
		List<Envelope> envelopes = Database.getEnvelopes();
		
		
		int sum = 0;
		for(int index = 0; index < envelopes.size(); index++) {
			if(e.equals(envelopes.get(index))) continue;
			if(envelopes.get(index).getFillSetting() == EnvelopeSettings.percentage) {
				sum += envelopes.get(index).getFillAmount();
			}
		}
		
		return sum;
	}
	
}
